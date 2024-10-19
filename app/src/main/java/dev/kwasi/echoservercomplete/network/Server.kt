package dev.kwasi.echoservercomplete.network

import android.util.Log
import com.google.gson.Gson
import dev.kwasi.echoservercomplete.models.ContentModel
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectInterface
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.Exception
import kotlin.concurrent.thread
import dev.kwasi.echoservercomplete.models.Encryption

/// The [Server] class has all the functionality that is responsible for the 'server' connection.
/// This is implemented using TCP. This Server class is intended to be run on the GO.

class Server(private val iFaceImpl:NetworkMessageInterface) {
    companion object {
        const val PORT: Int = 9999
        const val IP: String = "192.168.49.1"

    }

    private val svrSocket: ServerSocket = ServerSocket(PORT, 0, InetAddress.getByName(IP))
    private val clientMap: HashMap<String, Socket> = HashMap()
    private val attendeesList: MutableList<String> = mutableListOf() // Attendees list
    private val challengeList: HashMap<String, String> = HashMap()
    private val authorizedList: MutableList<String> = mutableListOf()
    private val classStudentIds = listOf(
        "816032311", "816117992", "816001234", "816002345", "816003456", "816004567",
        "816005678", "816006789", "816007890", "816008901", "816009012", "816010123",
        "816011234", "816012345", "816013456", "816014567", "816015678", "816016789",
        "816017890", "816018901", "816019012", "816020123", "816021234", "816022345",
        "816023456", "816024567", "816025678", "816026789", "816027890", "816028901"
    )

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

                            val studentId = clientContent.studentId
                            if (studentId != null){
                                if (!classStudentIds.contains(studentId)) {
                                    // Client is not a student of the class, kick the client out
                                    Log.e("SERVER", "Student $studentId is not of the class")
                                    removeClient(studentId)
                                    break
                                }

                                // Initiate Challenge Response protocol
                                if (clientContent.message == "I am here" && !authorizedList.contains(studentId)) {
                                    val randomR = Encryption.rand().toString()
                                    challengeList[studentId] = randomR
                                    sendMessageWithSocket(socket,ContentModel(randomR,IP,studentId))
                                    Log.e("SERVER", "Started Challenge for student $studentId")

                                } else if (challengeList[studentId] != null) {
                                    // Continue Challenge Response Protocol
                                    val randomR = challengeList[studentId]
                                    val encryptedMessage = clientContent.message
                                    val decryptedMessage = Encryption.decryptWithID(studentId, encryptedMessage)
                                    if (randomR == decryptedMessage) {
                                        // Student is verified who they say they are
                                        challengeList.remove(studentId)
                                        authorizedList.add(studentId)
                                        if (clientMap[studentId]==null) {
                                            // Set student ID if it doesn't exist
                                            clientMap[studentId] = socket
                                            addAttendee(studentId)
                                        }
                                        Log.e("SERVER", "Adding student $studentId to clientMap")
                                    } else {
                                        // Failed authentication, kick the client out
                                        Log.e("SERVER", "Failed authentication for student $studentId")
                                        removeClient(studentId)
                                        break
                                    }
                                } else if (clientMap[studentId]!=null) {
                                    // Student can send messages once registered
                                    iFaceImpl.onContent(clientContent)
                                }

                            }


                        }
                    } catch (e: Exception){
                        Log.e("SERVER", "An error has occurred with the client $it: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun sendMessageWithSocket(socket: Socket, content: ContentModel) {
        val writer = socket.outputStream.bufferedWriter()
        val newContent = ContentModel(content.message, IP, content.studentId)
        val contentStr = Gson().toJson(newContent)
        writer.write("$contentStr\n")
        writer.flush()
    }

    fun sendMessageToClient(content: ContentModel) {
        val socket = clientMap[content.studentId]
        if (socket != null) {
            val writer = socket.outputStream.bufferedWriter()
            val newContent = ContentModel(content.message, IP, content.studentId)
            val contentStr = Gson().toJson(newContent)
            writer.write("$contentStr\n")
            writer.flush()
        } else {
            Log.e("SERVER", "Student with ID ${content.studentId} not found.")
        }
    }

    private fun addAttendee(studentId: String) {
        if (!attendeesList.contains(studentId)) {
            attendeesList.add(studentId)
            Log.e("SERVER", "Added attendee: $studentId")

            iFaceImpl.onAttendeeListUpdated(attendeesList)
        }
    }

    private fun removeClient(studentId: String) {
        val socket = clientMap[studentId]

        if (socket != null) {
            // Close the client's socket connection
            try {
                socket.close()
                Log.e("SERVER", "Closed connection for $studentId")
            } catch (e: Exception) {
                Log.e("SERVER", "Error closing socket for $studentId")
            }
        }

        // Remove the student from the various lists
        clientMap.remove(studentId)
        authorizedList.remove(studentId)
        attendeesList.remove(studentId)
        challengeList.remove(studentId)

        // Notify the UI or relevant listeners that the attendee list has been updated
        iFaceImpl.onAttendeeListUpdated(attendeesList)
        Log.e("SERVER", "Removed student $studentId from group")
    }

    fun close(){
        svrSocket.close()
        clientMap.clear()
        attendeesList.clear()
        challengeList.clear()
        authorizedList.clear()
    }

}