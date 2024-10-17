package dev.kwasi.echoservercomplete

import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.kwasi.echoservercomplete.chatlist.ChatListAdapter
import dev.kwasi.echoservercomplete.models.ContentModel
import dev.kwasi.echoservercomplete.network.Client
import dev.kwasi.echoservercomplete.network.NetworkMessageInterface
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapter
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapterInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectManager
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.text.Charsets.UTF_8

class CommunicationActivity : AppCompatActivity(), WifiDirectInterface, PeerListAdapterInterface, NetworkMessageInterface {
    private var wfdManager: WifiDirectManager? = null

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private var peerListAdapter:PeerListAdapter? = null
    private var chatListAdapter:ChatListAdapter? = null

    private var wfdAdapterEnabled = false
    private var wfdHasConnection = false
    private var hasDevices = false
    private var client: Client? = null
    private var deviceIp: String = ""
    private var studentId: String = ""
    private var authorized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_communication)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val manager: WifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(this, mainLooper, null)
        wfdManager = WifiDirectManager(manager, channel, this)

        peerListAdapter = PeerListAdapter(this)
        val rvPeerList: RecyclerView= findViewById(R.id.rvPeerListing)
        rvPeerList.adapter = peerListAdapter
        rvPeerList.layoutManager = LinearLayoutManager(this)

        chatListAdapter = ChatListAdapter()
        val rvChatList: RecyclerView = findViewById(R.id.rvChat)
        rvChatList.adapter = chatListAdapter
        rvChatList.layoutManager = LinearLayoutManager(this)

        // removeGroup
        wfdManager?.disconnect()
    }

    override fun onResume() {
        super.onResume()
        wfdManager?.also {
            registerReceiver(it, intentFilter)
        }
    }

    override fun onPause() {
        super.onPause()
        wfdManager?.also {
            unregisterReceiver(it)
        }
    }
    fun createGroup(view: View) {
        wfdManager?.createGroup()
    }

    fun discoverNearbyPeers(view: View) {
        studentId = findViewById<EditText>(R.id.etStudentId).text.toString()
        if (studentId.isNotBlank()) {
            wfdManager?.discoverPeers()
        } else {
            Toast.makeText(this,"Enter a valid Student Id", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI(){
        val wfdAdapterErrorView:ConstraintLayout = findViewById(R.id.clWfdAdapterDisabled)
        wfdAdapterErrorView.visibility = if (!wfdAdapterEnabled) View.VISIBLE else View.GONE

        val wfdNoConnectionView:ConstraintLayout = findViewById(R.id.clNoWifiDirectConnection)
        wfdNoConnectionView.visibility = if (wfdAdapterEnabled && !wfdHasConnection) View.VISIBLE else View.GONE

        val rvPeerList: RecyclerView= findViewById(R.id.rvPeerListing)
        rvPeerList.visibility = if (wfdAdapterEnabled && !wfdHasConnection && hasDevices) View.VISIBLE else View.GONE

        val wfdConnectedView:ConstraintLayout = findViewById(R.id.clHasConnection)
        wfdConnectedView.visibility = if(wfdHasConnection)View.VISIBLE else View.GONE
    }

    fun sendMessage(view: View) {
        val etMessage:EditText = findViewById(R.id.etMessage)
        val etString = etMessage.text.toString()

        val clientContent = ContentModel(etString, deviceIp, studentId)
        etMessage.text.clear()
        chatListAdapter?.addItemToEnd(clientContent)

        // Encrypt Message
        val aesKey = generateAESKey(studentId)
        val aesIv = generateIV(studentId)
        val encryptedText = encryptMessage(etString, aesKey, aesIv)
        val encryptedContent = ContentModel(encryptedText, deviceIp, studentId)
        client?.sendMessage(encryptedContent)

    }

    override fun onWiFiDirectStateChanged(isEnabled: Boolean) {
        wfdAdapterEnabled = isEnabled
        var text = "There was a state change in the WiFi Direct. Currently it is "
        text = if (isEnabled){
            "$text enabled!"
        } else {
            "$text disabled! Try turning on the WiFi adapter"
        }

        val toast = Toast.makeText(this, text, Toast.LENGTH_SHORT)
        toast.show()
    }

    override fun onPeerListUpdated(deviceList: Collection<WifiP2pDevice>) {
        val toast = Toast.makeText(this, "Updated listing of nearby classes", Toast.LENGTH_SHORT)
        toast.show()
        hasDevices = deviceList.isNotEmpty()
        peerListAdapter?.updateList(deviceList)
        updateUI()
    }

    override fun onGroupStatusChanged(groupInfo: WifiP2pGroup?) {
        wfdHasConnection = groupInfo != null
        val tvNetworkInfo = findViewById<TextView>(R.id.tvNetworkInfo)

        if (groupInfo == null){
            tvNetworkInfo.text = "Network not connected"
            client?.close()
        } else if (!groupInfo.isGroupOwner && client == null) {
            client = Client(this)
            deviceIp = client!!.ip
            studentId = findViewById<EditText>(R.id.etStudentId).text.toString()
            tvNetworkInfo.text = "Class Network: ${groupInfo.networkName}"
            // Initiate Challenge Response Protocol
            client?.sendMessage(ContentModel("I am here", deviceIp, studentId))
        }
        updateUI()
    }

    override fun onDeviceStatusChanged(thisDevice: WifiP2pDevice) {

    }

    override fun onPeerClicked(peer: WifiP2pDevice) {
        studentId = findViewById<EditText>(R.id.etStudentId).text.toString()
        if (studentId.isNotBlank()) {
            wfdManager?.connectToPeer(peer)
        } else {
            Toast.makeText(this,"Enter a valid Student Id", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onContent(content: ContentModel) {
        runOnUiThread{
            val sentStudentId = content.studentId
            val message = content.message
            val aesKey = generateAESKey(sentStudentId)
            val aesIv = generateIV(sentStudentId)

            if (!authorized) {
                // This should be random R, so encrypt and return
                val encryptedMessage = encryptMessage(message, aesKey, aesIv)
                client?.sendMessage(ContentModel(encryptedMessage, deviceIp, studentId))
                authorized = true
            } else {
                // Authorized to receive messages, must be decrypted
                val decryptedMessage = decryptMessage(message, aesKey, aesIv)
                val decryptedContent = ContentModel(decryptedMessage, content.senderIp, sentStudentId, content.timestamp)
                chatListAdapter?.addItemToEnd(decryptedContent)
            }

        }
    }

    private fun hashStrSha256(str: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(str.toByteArray(UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun generateAESKey(seed: String): SecretKeySpec {
        val keyBytes = hashStrSha256(seed).substring(0, 32).toByteArray(UTF_8)
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun generateIV(seed: String): IvParameterSpec {
        val ivBytes = seed.substring(0, 16).toByteArray(UTF_8)
        return IvParameterSpec(ivBytes)
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun encryptMessage(plaintext: String, aesKey: SecretKeySpec, aesIv: IvParameterSpec): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, aesIv)
        val encrypted = cipher.doFinal(plaintext.toByteArray())
        return Base64.Default.encode(encrypted)
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun decryptMessage(encryptedText: String, aesKey: SecretKeySpec, aesIv: IvParameterSpec): String {
        val decodedBytes = Base64.Default.decode(encryptedText)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, aesIv)
        return String(cipher.doFinal(decodedBytes))
    }


}