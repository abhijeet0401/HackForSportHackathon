package com.example.health

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract.createDocument
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.bumptech.glide.Glide
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.firebase.ui.database.SnapshotParser
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import de.hdodenhof.circleimageview.CircleImageView
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject


class ChatFragment : Fragment(), GoogleApiClient.OnConnectionFailedListener {
    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.d(TAG, "onConnectionFailed:$connectionResult");
        Toast.makeText(activity, "Google Play Services error.", Toast.LENGTH_SHORT).show();
    }

    companion object {

        private const val TAG = "MainActivity"
        const val ANONYMOUS = "anonymous"
        const val MESSAGE_CHILD = "messages"
        const val REQUEST_IMAGE = 1
        const val LOADING_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/b/b1/Loading_icon.gif"
    }

    private lateinit var add_image_image_view: ImageView
    private var userName: String? = null
    private var userPhotoUrl: String? = null
    private lateinit var text_message_edit_text: EditText
    private var fireBaseAuth: FirebaseAuth? = null
    private var firebaseUser: FirebaseUser? = null

    private var googleApiClient: GoogleApiClient? = null

    lateinit var linearLayoutManager: LinearLayoutManager

    private var firebaseDatabaseReference: DatabaseReference? = null
    private var firebaseAdapter: FirebaseRecyclerAdapter<Message, MessageViewHolder>? = null
    private lateinit var progress_bar: ProgressBar
    private var googleSignInClient: GoogleSignInClient? = null
    private lateinit var send_button: Button
    private lateinit var recycler_view: RecyclerView
    private lateinit var stringFromExcersiceFragment: String


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)
        progress_bar = view.findViewById(R.id.progress_bar)
        add_image_image_view = view.findViewById(R.id.add_image_image_view)
        send_button = view.findViewById(R.id.send_button)
        recycler_view = view.findViewById(R.id.recycler_view)
        text_message_edit_text = view.findViewById(R.id.text_message_edit_text)

        linearLayoutManager = LinearLayoutManager(activity)
        linearLayoutManager.stackFromEnd = true

        firebaseDatabaseReference =
            FirebaseDatabase.getInstance("https://health-cdbab-default-rtdb.firebaseio.com/").reference

        userName = ANONYMOUS

        fireBaseAuth = FirebaseAuth.getInstance()
        firebaseUser = fireBaseAuth!!.currentUser


        if (firebaseUser == null) {
            Log.d(TAG, "USER IS NULL: $firebaseUser")

            startActivity(Intent(requireContext(), SignInActivity::class.java))


        } else {
            userName = firebaseUser!!.displayName
            if (firebaseUser!!.photoUrl != null) {
                userPhotoUrl = firebaseUser!!.photoUrl!!.toString()
            }
            Log.d(TAG, "USER IS NOT NULL")
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)

        val parser = SnapshotParser<Message> { snapshot: DataSnapshot ->

            val chatMessage = snapshot.getValue(Message::class.java)
            if (chatMessage != null) {
                chatMessage.id = snapshot.key
            }
            chatMessage!!
        }

        val messageRefs = firebaseDatabaseReference!!.child(MESSAGE_CHILD)

        val options = FirebaseRecyclerOptions.Builder<Message>()
            .setQuery(messageRefs, parser)
            .build()

        firebaseAdapter = object : FirebaseRecyclerAdapter<Message, MessageViewHolder>(options) {
            override fun onCreateViewHolder(viewGroup: ViewGroup, p1: Int): MessageViewHolder {

                val inflater = LayoutInflater.from(viewGroup.context)





                return MessageViewHolder(inflater.inflate(R.layout.item_message, viewGroup, false))
            }

            override fun onBindViewHolder(
                holder: MessageViewHolder,
                position: Int,
                model: Message
            ) {

                progress_bar.visibility = ProgressBar.INVISIBLE

                holder.bind(model)


            }

        }
        firebaseAdapter!!.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                val messageCount = firebaseAdapter!!.itemCount
                val lastVisiblePosition =
                    linearLayoutManager.findLastCompletelyVisibleItemPosition()

                if (lastVisiblePosition == -1 || positionStart >= messageCount - 1 && lastVisiblePosition == positionStart - 1) {
                    recycler_view!!.scrollToPosition(positionStart)
                }
            }
        })



        recycler_view!!.layoutManager = linearLayoutManager
        recycler_view!!.adapter = firebaseAdapter

        add_image_image_view.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)

            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_IMAGE)
        }

        send_button.setOnClickListener {
            if (text_message_edit_text.text.toString() == "send my data") {
                setFragmentResultListener("requestKey") { requestKey, bundle ->

                    stringFromExcersiceFragment = bundle.getString("bundleKey").toString()

                }
                val message =
                    Message(stringFromExcersiceFragment.toString(), userName!!, userPhotoUrl, null)

                firebaseDatabaseReference!!.child(MESSAGE_CHILD).push().setValue(message)
                text_message_edit_text!!.setText("")

            }
            if (text_message_edit_text.text.toString().endsWith("calorie")) {
                var food: String = text_message_edit_text.text.toString().substring(
                    0, text_message_edit_text.text.toString().indexOf("")
                )
                val api: String =
                    "https://api.nal.usda.gov/fdc/v1/foods/search?api_key=lmdeQ81lKJ4jnI8xL4GfqBsfE6iCnE1YL1a1lvqu&query=$food"
                val queue = Volley.newRequestQueue(activity)
                val stringRequest = StringRequest(
                    Request.Method.GET, api,
                    { response ->
                        extractJSON(response)

                    }, { error ->
                        Toast.makeText(activity, "API ERROR", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "onConnectionFailed:$error");
                    })
                queue.add(stringRequest)


            }
            if (text_message_edit_text.text.toString() == "make appointment through sms") {
                val api: String =
                    "https://rapidapi.rmlconnect.net:9443/bulksms/bulksms?username=rapid-n7553407010000&password=617bf1cd245383001100f7e5&type=0&dlr=1&destination=+919370556094&source=RMLPRD&message=Hi Abhijeet Singh,\n" +
                            "Thank you for choosing Mock hospital for your Health checkup. You have scheduled an\n" +
                            "appointment with us for Tomorrow. don't take any medication within 8 hours of your appointment.\n"
                val queue = Volley.newRequestQueue(activity)
                val stringRequest = StringRequest(
                    Request.Method.GET, api,
                    { response ->
                        Toast.makeText(
                            activity,
                            "appointment successful through sms",
                            Toast.LENGTH_SHORT
                        ).show()


                    }, { error ->
                        Toast.makeText(activity, "API ERROR", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "onConnectionFailed:$error");
                    })
                queue.add(stringRequest)
                text_message_edit_text!!.setText("")

            }
            if (text_message_edit_text.text.toString() == "send health data through sms") {
                setFragmentResultListener("requestKey") { requestKey, bundle ->

                    stringFromExcersiceFragment = bundle.getString("bundleKey").toString()

                }
                val api: String =
                    "https://rapidapi.rmlconnect.net:9443/bulksms/bulksms?username=rapid-n7553407010000&password=617bf1cd245383001100f7e5&type=0&dlr=1&destination=+919370556094&source=RMLPRD&message=${stringFromExcersiceFragment}"

                val queue = Volley.newRequestQueue(activity)
                val stringRequest = StringRequest(
                    Request.Method.GET, api,
                    { response ->
                        Toast.makeText(
                            activity,
                            "Health data sent successfully through sms",
                            Toast.LENGTH_SHORT
                        ).show()


                    }, { error ->
                        Toast.makeText(activity, "API ERROR", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "onConnectionFailed:$error");
                    })
                queue.add(stringRequest)
                text_message_edit_text!!.setText("")

            }
            if (text_message_edit_text.text.toString() == "make appointment through wbs") {

                val stringRequest: StringRequest = object :
                    StringRequest(Method.POST, "https://rapidapi.rmlconnect.net/wbm/v1/message",
                        Response.Listener { response ->


                            try {
                                Toast.makeText(
                                    activity,
                                    "appointment successful through whatsapp business messaging",
                                    Toast.LENGTH_SHORT
                                ).show()

                            } catch (e: JSONException) {
                                e.printStackTrace()
                            }
                        },
                        Response.ErrorListener { error ->
                            Toast.makeText(activity, error.toString(), Toast.LENGTH_LONG).show()
                        }) {
                    override fun getBody(): ByteArray {
                        val params2 = JSONObject()
                        params2.put("phone", "+917057488041")
                        val jsonArray = JSONArray()
                        val Username1: String = "Abhijeet Singh"
                        val HospitalName: String = "Mock"
                        val Date: String = "Tomorrow"

                        val object1 = JSONObject()
                        object1.put("text", Username1)
                        val object2 = JSONObject()
                        object2.put("text",HospitalName)
                        val object3 = JSONObject()
                        object3.put("text",Date)
                        jsonArray.put(object1)
                        jsonArray.put(object2)
                        jsonArray.put(object3)
                        val params3 = JSONObject()

                        params3.put("type", "media_template")
                        params3.put("template_name", "schedule_appointment")
                        params3.put("lang_code", "en")
                        params3.put("body", jsonArray)

                        params2.put("media", params3)
                        return params2.toString().toByteArray()
                    }

                    override fun getHeaders(): Map<String, String> {
                        val params: MutableMap<String, String> = HashMap()
                        params["Content-Type"] = "application/json"
                        params["Authorization"] = "617bf1cd245383001100f7e5"

                        return params
                    }
                }
                val requestQueue = Volley.newRequestQueue(activity)
                requestQueue.add(stringRequest)
                text_message_edit_text!!.setText("")
            }
            if (text_message_edit_text.text.toString() == "send health data through wbs") {
                setFragmentResultListener("requestKey") { requestKey, bundle ->

                    stringFromExcersiceFragment = bundle.getString("bundleKey").toString()

                }
                val stringRequest: StringRequest = object :
                    StringRequest(Method.POST, "https://rapidapi.rmlconnect.net/wbm/v1/message",
                        Response.Listener { response ->


                            try {
                                Toast.makeText(
                                    activity,
                                    "Health data sent successfully through whatsapp business messaging",
                                    Toast.LENGTH_SHORT
                                ).show()

                            } catch (e: JSONException) {
                                e.printStackTrace()
                            }
                        },
                        Response.ErrorListener { error ->
                            Toast.makeText(activity, error.toString(), Toast.LENGTH_LONG).show()
                        }) {
                    override fun getBody(): ByteArray {
                        val params2 = JSONObject()
                        params2.put("phone", "+917057488041")
                        params2.put("text",stringFromExcersiceFragment)
                        return params2.toString().toByteArray()
                    }

                    override fun getHeaders(): Map<String, String> {
                        val params: MutableMap<String, String> = HashMap()
                        params["Content-Type"] = "application/json"
                        params["Authorization"] = "617bf1cd245383001100f7e5"

                        return params
                    }
                }
                val requestQueue = Volley.newRequestQueue(activity)
                requestQueue.add(stringRequest)
                text_message_edit_text!!.setText("")
            }
            else {


                val message =
                    Message(
                        text_message_edit_text!!.text.toString(),
                        userName!!,
                        userPhotoUrl,
                        null
                    )

                firebaseDatabaseReference!!.child(MESSAGE_CHILD).push().setValue(message)
                text_message_edit_text!!.setText("")
            }


        }


        return view
    }

    override fun onStart() {
        super.onStart()
        firebaseAdapter?.startListening()
    }

    override fun onStop() {
        super.onStop()
        firebaseAdapter?.stopListening()
    }

    class MessageViewHolder(v: View) : RecyclerView.ViewHolder(v) {

        lateinit var message: Message

        var messageTextView: TextView
        var messageImageView: ImageView
        var nameTextView: TextView
        var userImage: CircleImageView

        init {
            messageTextView = itemView.findViewById(R.id.message_text_view)
            messageImageView = itemView.findViewById(R.id.message_image_view)
            nameTextView = itemView.findViewById(R.id.name_text_view)
            userImage = itemView.findViewById(R.id.messenger_image_view)
        }

        fun bind(message: Message) {
            this.message = message

            if (message.text != null) {
                messageTextView.text = message.text

                messageTextView.visibility = View.VISIBLE

                messageImageView.visibility = View.GONE
                Log.e(TAG, "Getting Download url was not successful")

            } else if (message.imageUrl != null) {

                messageTextView.visibility = View.GONE
                messageImageView.visibility = View.VISIBLE

                val imageUrl = message.imageUrl

                if (imageUrl!!.startsWith("gs://")) {
                    val storageReference =
                        FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl)

                    storageReference.downloadUrl.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val downloadUrl = task.result!!.toString()

                            Glide.with(messageImageView.context)
                                .load(downloadUrl)
                                .into(messageImageView)
                        } else {
                            Log.e(TAG, "Getting Download url was not successful ${task.exception}")
                        }
                    }
                } else {
                    Glide.with(messageImageView.context)
                        .load(Uri.parse(message.imageUrl))
                        .into(messageImageView)
                }

            }

            nameTextView.text = message.name

            if (message.photoUrl == null) {
                userImage.setImageDrawable(
                    ContextCompat.getDrawable(
                        userImage.context,
                        R.drawable.ic_account_circle
                    )
                )
            } else {
                Glide.with(userImage.context)
                    .load(Uri.parse(message.photoUrl))
                    .into(userImage)
            }


        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_IMAGE) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {

                    val uri = data.data

                    val tempMessage = Message(null, userName, userPhotoUrl, LOADING_IMAGE_URL)
                    firebaseDatabaseReference!!.child(MESSAGE_CHILD).push()
                        .setValue(tempMessage) { databaseError, databaseReference ->
                            if (databaseError == null) {
                                val key = databaseReference.key
                                val storageReference = FirebaseStorage.getInstance()
                                    .getReference(firebaseUser!!.uid)
                                    .child(key!!)
                                    .child(uri!!.lastPathSegment!!)

                                putImageInStorage(storageReference, uri, key)
                            } else {
                                Log.e(
                                    TAG,
                                    "Unable to write message to database ${databaseError.toException()}"
                                )
                            }
                        }
                }
            }
        }
    }

    private fun extractJSON(response: String) {
        val jsonObject = JSONObject(response)
        val foods = jsonObject.getJSONArray("foods")
        val foodsobject = foods.getJSONObject(0)
        val foodCalorie = foodsobject.getJSONArray("foodNutrients")
        val list = mutableListOf<Data>()
        for (i in 0..9) {
            val item = foodCalorie.getJSONObject(i)
            val nutrientName = item.getString("nutrientName")
            val value = item.getString("value")
            val data = Data(nutrientName, value)
            list.add(data)
        }

        val message =
            Message(
                list.toString(),
                userName!!,
                userPhotoUrl,
                null
            )

        firebaseDatabaseReference!!.child(MESSAGE_CHILD).push().setValue(message)
        text_message_edit_text!!.setText("")

    }

    private fun putImageInStorage(storageReference: StorageReference, uri: Uri?, key: String?) {
        val uploadTask = storageReference.putFile(uri!!)
        uploadTask.continueWithTask { task ->
            if (!task.isSuccessful) {
                throw task.exception!!
            }
            storageReference.downloadUrl
        }.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val downloadUrl = task.result!!.toString()
                val message = Message(null, userName, userPhotoUrl, downloadUrl)

                firebaseDatabaseReference!!.child(MESSAGE_CHILD).child(key!!).setValue(message)
            } else {
                Log.e(TAG, "Image upload task was not successful ${task.exception}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        firebaseAdapter!!.startListening()
    }

    override fun onPause() {
        super.onPause()


        firebaseAdapter!!.stopListening()
    }
}