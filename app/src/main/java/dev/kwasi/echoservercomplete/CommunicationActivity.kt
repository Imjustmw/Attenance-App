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
import dev.kwasi.echoservercomplete.network.NetworkMessageInterface
import dev.kwasi.echoservercomplete.network.Server
import dev.kwasi.echoservercomplete.peerlist.AttendeeListAdapter
import dev.kwasi.echoservercomplete.peerlist.AttendeeListAdapterInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectManager

class CommunicationActivity : AppCompatActivity(), WifiDirectInterface, AttendeeListAdapterInterface, NetworkMessageInterface {
    private var wfdManager: WifiDirectManager? = null

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private var attendeeListAdapter:AttendeeListAdapter? = null
    private var chatListAdapter:ChatListAdapter? = null

    private var wfdAdapterEnabled = false
    private var wfdHasConnection = false
    private var hasDevices = false
    private var server: Server? = null
    private var deviceIp: String = ""
    private var selectedStudent: String? = null
    private val peerMessagesMap: HashMap<String, MutableList<ContentModel>> = HashMap()
    private val serverMessagesMap: HashMap<String, MutableList<ContentModel>> = HashMap()

    private val challengeMap = mutableMapOf<String, String>()
    private val authenticateStudents = mutableSetOf<String>()


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

        chatListAdapter = ChatListAdapter()
        val rvChatList: RecyclerView = findViewById(R.id.rvChat)
        rvChatList.adapter = chatListAdapter
        rvChatList.layoutManager = LinearLayoutManager(this)

        attendeeListAdapter = AttendeeListAdapter(this)
        val rvPeerList: RecyclerView= findViewById(R.id.rvAttendees)
        rvPeerList.adapter = attendeeListAdapter
        rvPeerList.layoutManager = LinearLayoutManager(this)



        // remove existing group if any
        wfdManager?.disconnect()
    }

    override fun onResume() {
        super.onResume()
        wfdManager?.also {
            registerReceiver(it, intentFilter)
            it.requestPeers()
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

    fun removeGroup(view: View) {
        if (server != null) {
            server?.close()
            server = null
        }
        wfdManager?.disconnect()
    }

    private fun updateUI(){
        val wfdAdapterErrorView:ConstraintLayout = findViewById(R.id.clWfdAdapterDisabled)
        wfdAdapterErrorView.visibility = if (!wfdAdapterEnabled) View.VISIBLE else View.GONE

        val wfdNoConnectionView:ConstraintLayout = findViewById(R.id.clNoWifiDirectConnection)
        wfdNoConnectionView.visibility = if (wfdAdapterEnabled && !wfdHasConnection) View.VISIBLE else View.GONE

        val wfdConnectedView:ConstraintLayout = findViewById(R.id.clHasConnection)
        wfdConnectedView.visibility = if(wfdHasConnection)View.VISIBLE else View.GONE
    }

    private fun updateChatUI(studentId: String) {
        val studentMessages = peerMessagesMap[studentId] ?: mutableListOf()
        val serverMessages = serverMessagesMap[studentId] ?: mutableListOf()

        val messages = (studentMessages + serverMessages).sortedBy { it.timestamp }
        chatListAdapter?.updateChat(messages)

        val clChatInterface:ConstraintLayout = findViewById(R.id.clChatInterface)
        clChatInterface.visibility = if (selectedStudent != null)View.VISIBLE else View.GONE
    }

    // New function to handle sending messages in a background thread
    private fun sendMessageToClient(content: ContentModel) {
        Thread {
            server?.sendMessageToClient(content)
        }.start()
    }

    fun sendMessage(view: View) {
        val etMessage:EditText = findViewById(R.id.etMessage)
        val etString = etMessage.text.toString()

        if (selectedStudent != null) {
            val serverContent = ContentModel(etString, deviceIp, selectedStudent)
            serverMessagesMap.getOrPut(selectedStudent!!) { mutableListOf() }.add(serverContent)
            etMessage.text.clear()
            chatListAdapter?.addItemToEnd(serverContent)

            // Encrypt Message
            val encryptedMessage = Encryption.encryptWithID(selectedStudent!!, etString)
            val encryptedContent = ContentModel(encryptedMessage, deviceIp, selectedStudent)
            sendMessageToClient(encryptedContent)
        } else {
            Log.e("Chat", "No peer selected. Cannot send message")
        }
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
        updateUI()
    }

    override fun onGroupStatusChanged(groupInfo: WifiP2pGroup?) {
        wfdHasConnection = groupInfo != null

        val tvNetworkInfo = findViewById<TextView>(R.id.tvNetworkInfo)
        if (groupInfo == null){
            server?.close()
            server = null
            tvNetworkInfo.text = "Not connected to network"
        } else if (groupInfo.isGroupOwner && server == null){
            server = Server(this)
            deviceIp = "192.168.49.1"

            val ssid = groupInfo.networkName
            val password = groupInfo.passphrase
            tvNetworkInfo.text = "Class Network: $ssid\nNetwork Password: $password"
        }
        updateUI()
    }

    override fun onDeviceStatusChanged(thisDevice: WifiP2pDevice) {
    }

    override fun onPeerListUpdated(deviceList: Collection<WifiP2pDevice>) {

    }

    override fun onAttendeeListUpdated(attendees: List<String>) {
        runOnUiThread {
            hasDevices = attendees.isNotEmpty()
            attendeeListAdapter?.updateList(attendees)

            // reset UI if selected Student left
            if (selectedStudent != null && !attendees.contains(selectedStudent)){
                selectedStudent = null
                findViewById<TextView>(R.id.tvStudentChat).text = "Student"
                updateChatUI(selectedStudent!!)
            }
            updateUI()
        }
    }

    override fun onAttendeeClicked(studentId: String) {
        selectedStudent = studentId
        findViewById<TextView>(R.id.tvStudentChat).text = "Student: $studentId"
        updateChatUI(studentId)
    }

    override fun onContent(content: ContentModel) {
        runOnUiThread {
            val studentId = content.studentId
            val decryptedMessage = Encryption.decryptWithID(studentId!!, content.message)
            val decryptedContent = ContentModel(decryptedMessage, content.senderIp, studentId, content.timestamp)
            peerMessagesMap.getOrPut(studentId) { mutableListOf()}.add(decryptedContent)
            chatListAdapter?.addItemToEnd(decryptedContent)
        }
    }

}