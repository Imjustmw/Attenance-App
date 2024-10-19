package dev.kwasi.echoservercomplete.network

import android.net.wifi.p2p.WifiP2pDevice
import android.util.Log
import com.google.gson.Gson
import dev.kwasi.echoservercomplete.models.ContentModel
import dev.kwasi.echoservercomplete.models.Encryption
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.Socket
import kotlin.concurrent.thread

class Client (private val networkMessageInterface: NetworkMessageInterface, private val studentId: String){
    private lateinit var clientSocket: Socket
    private lateinit var reader: BufferedReader
    private lateinit var writer: BufferedWriter
    private var authenticated = false
    var ip:String = ""

    init {
        thread {
            clientSocket = Socket("192.168.49.1", 9999)
            reader = clientSocket.inputStream.bufferedReader()
            writer = clientSocket.outputStream.bufferedWriter()
            ip = clientSocket.inetAddress.hostAddress!!

            // Send Challenge Protocol
            val challengeProtocol = ContentModel(
                message = "I am here",
                senderIp = ip,
                studentId = studentId,
            )
            sendMessage(challengeProtocol)

            while(true){
                try{
                    val serverResponse = reader.readLine()
                    if (serverResponse != null){
                        val serverContent = Gson().fromJson(serverResponse, ContentModel::class.java)

                        if (!authenticated) {
                            try {
                                val number = serverContent.message.toInt() // throw except if not number
                                val encryptedMessage = Encryption.encryptWithID(studentId, serverContent.message)
                                sendMessage(ContentModel(encryptedMessage, ip, studentId))
                                authenticated = true
                            } catch (e: NumberFormatException) {
                                Log.e("CLIENT", "Received message is not a valid number: ${serverContent.message}")
                            }
                        } else {
                            // Display content received from server once authenticated
                            networkMessageInterface.onContent(serverContent)
                        }
                    }
                } catch(e: Exception){
                    Log.e("CLIENT", "An error has occurred in the client")
                    e.printStackTrace()
                    break
                }
            }
        }
    }

    fun sendMessage(content: ContentModel){
        thread {
            if (!clientSocket.isConnected){
                throw Exception("We aren't currently connected to the server!")
            }
            val contentAsStr:String = Gson().toJson(content)
            writer.write("$contentAsStr\n")
            writer.flush()
        }

    }

    fun close(){
        clientSocket.close()
    }
}