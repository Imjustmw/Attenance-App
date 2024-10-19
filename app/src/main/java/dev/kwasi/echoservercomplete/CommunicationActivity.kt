package dev.kwasi.echoservercomplete

import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.util.Log
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
import dev.kwasi.echoservercomplete.models.Encryption
import dev.kwasi.echoservercomplete.network.Client
import dev.kwasi.echoservercomplete.network.NetworkMessageInterface
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapter
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapterInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectManager


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

    fun discoverNearbyPeers(view: View) {
        studentId = findViewById<EditText>(R.id.etStudentId).text.toString()
        if (studentId.isNotBlank()) {
            wfdManager?.discoverPeers()
        } else {
            Toast.makeText(this,"Enter a valid Student Id", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI(){
        Log.d("Communication", "wfdAdapter: $wfdAdapterEnabled, wfdHasConnection: $wfdHasConnection")
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
        val encryptedText = Encryption.encryptWithID(studentId, etString)
        val encryptedContent = ContentModel(encryptedText, deviceIp, studentId)
        client?.sendMessage(encryptedContent)

    }

    override fun onWiFiDirectStateChanged(isEnabled: Boolean) {
        wfdAdapterEnabled = isEnabled
        var text = "Wifi Direct State: "
        text = if (isEnabled){
            "$text enabled!"
        } else {
            "$text disabled!"
        }
        val toast = Toast.makeText(this, text, Toast.LENGTH_SHORT)
        toast.show()
        updateUI()
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
            studentId = findViewById<EditText>(R.id.etStudentId).text.toString()
            client = Client(this, studentId)
            deviceIp = client!!.ip
            tvNetworkInfo.text = "Class Network: ${groupInfo.networkName}"
        }
        Toast.makeText(this, "GroupStatus: $wfdHasConnection", Toast.LENGTH_SHORT).show()
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
            val decryptedMessage = Encryption.decryptWithID(content.studentId, content.message)
            val decryptedContent = ContentModel(decryptedMessage, content.senderIp, content.studentId, content.timestamp)
            chatListAdapter?.addItemToEnd(decryptedContent)
        }
    }

}