package dev.kwasi.echoservercomplete.network

import android.util.Log
import com.google.gson.Gson
import dev.kwasi.echoservercomplete.models.ContentModel
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.Exception
import kotlin.concurrent.thread

/// The [Server] class has all the functionality that is responsible for the 'server' connection.
/// This is implemented using TCP. This Server class is intended to be run on the GO.

class Server(private val iFaceImpl:NetworkMessageInterface) {
    companion object {
        const val PORT: Int = 9999

    }

    private val svrSocket: ServerSocket = ServerSocket(PORT, 0, InetAddress.getByName("192.168.49.1"))
    private val clientMap: HashMap<String, Socket> = HashMap()
    private val studentIdMap: MutableMap<String, String?> = mutableMapOf()
    val classStudentIds = listOf("student1", "student2", "student3")

    init {
        thread{
            while(true){
                try{
                    val clientConnectionSocket = svrSocket.accept()
                    Log.e("SERVER", "The server has accepted a connection: ")
                    handleSocket(clientConnectionSocket)

                }catch (e: Exception){
                    Log.e("SERVER", "An error has occurred in the server!")
                    e.printStackTrace()
                }
            }
        }
    }


    private fun handleSocket(socket: Socket){
        socket.inetAddress.hostAddress?.let {
            clientMap[it] = socket
            Log.e("SERVER", "A new connection has been detected!")
            thread {
                val clientReader = socket.inputStream.bufferedReader()
                val clientWriter = socket.outputStream.bufferedWriter()
                var receivedJson: String?

                while(socket.isConnected){
                    try{
                        receivedJson = clientReader.readLine()
                        if (receivedJson!= null){
                            Log.e("SERVER", "Received a message from client $it")
                            val clientContent = Gson().fromJson(receivedJson, ContentModel::class.java)
                            iFaceImpl.onContent(clientContent)

                            if (clientContent.studentId != null) {
                                studentIdMap[it] = clientContent.studentId
                            }
                        }
                    } catch (e: Exception){
                        Log.e("SERVER", "An error has occurred with the client $it")
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun sendMessageToClient(content: ContentModel) {
        val socket = clientMap.entries.find { it.value.inetAddress.hostAddress == content.senderIp}?.value
        if (socket != null) {
            val writer = socket.outputStream.bufferedWriter()
            val newContent = ContentModel(content.message, "192.168.49.1", content.studentId)
            val contentStr = Gson().toJson(newContent)
            writer.write("$contentStr\n")
            writer.flush()
        } else {
            Log.e("SERVER", "Student with ID ${content.studentId} not found.")
        }
    }

    fun getStudentIdByDeviceAddress(deviceAddress: String): String? {
        return studentIdMap[deviceAddress]
    }

    fun close(){
        svrSocket.close()
        clientMap.clear()
    }

}