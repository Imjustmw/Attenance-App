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
    private val deviceAddressMap: MutableMap<String, String?> = mutableMapOf()
    val classStudentIds = listOf("student1", "student2", "student3", "816032311")

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

                            val studentId = clientContent.studentId
                            val deviceAddress = clientContent.deviceAddress

                            if (studentId!= null && clientMap[studentId]==null) {
                                clientMap[studentId] = socket
                                if (deviceAddress!=null && deviceAddressMap[deviceAddress]==null){
                                    deviceAddressMap[deviceAddress] = studentId
                                }
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
        val socket = clientMap[content.studentId]
        if (socket != null) {
            val writer = socket.outputStream.bufferedWriter()
            val newContent = ContentModel(content.message, "192.168.49.1", content.studentId, content.deviceAddress)
            val contentStr = Gson().toJson(newContent)
            writer.write("$contentStr\n")
            writer.flush()
        } else {
            Log.e("SERVER", "Student with ID ${content.studentId} not found.")
        }
    }

    fun getStudentIdByDeviceAddress(deviceAddress: String): String? {
        return deviceAddressMap[deviceAddress]
    }

    fun close(){
        svrSocket.close()
        clientMap.clear()
    }

}