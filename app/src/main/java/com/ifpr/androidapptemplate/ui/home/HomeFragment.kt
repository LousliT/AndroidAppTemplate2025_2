package com.ifpr.androidapptemplate.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.gms.location.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.ifpr.androidapptemplate.R
import com.ifpr.androidapptemplate.baseclasses.Item
import com.ifpr.androidapptemplate.databinding.FragmentHomeBinding
import com.ifpr.androidapptemplate.ui.ai.AiLogicActivity
import kotlinx.coroutines.*
import java.util.Locale
import android.util.Base64

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var currentAddressTextView: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_home, container, false)

        inicializaGerenciamentoLocalizacao(view)

        val containerItens = view.findViewById<LinearLayout>(R.id.itemContainer)
        carregarItensMarketplace(containerItens)

        val fab = view.findViewById<FloatingActionButton>(R.id.fab_ai)
        fab.setOnClickListener {
            val context = view.context
            val intent = Intent(context, AiLogicActivity::class.java)
            context.startActivity(intent)
        }

        return view
    }

    // ----------------------------------------------------------------------
    // LOCALIZAÇÃO
    // ----------------------------------------------------------------------
    private fun inicializaGerenciamentoLocalizacao(view: View) {
        currentAddressTextView = view.findViewById(R.id.currentAddressTextView)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermission()
        } else {
            getCurrentLocation()
        }
    }

    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                Snackbar.make(
                    requireView(),
                    "Permissão negada para localização",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    displayAddress(location)
                }
            }
        }

        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            30000L
        ).setMinUpdateIntervalMillis(30000L).build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun displayAddress(location: Location) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                val address = addresses?.firstOrNull()?.getAddressLine(0) ?: "Endereço não encontrado"

                withContext(Dispatchers.Main) {
                    currentAddressTextView.text = address
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    currentAddressTextView.text = "Erro: ${e.message}"
                }
            }
        }
    }

    // ----------------------------------------------------------------------
    //          🔥 CARREGAR ITENS (COM SUAS REGRAS DO FIREBASE)
    // ----------------------------------------------------------------------
    private fun carregarItensMarketplace(container: LinearLayout) {

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val databaseRef = FirebaseDatabase.getInstance()
            .getReference("itens")
            .child(userId)

        databaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                container.removeAllViews()

                if (!snapshot.exists()) return

                for (itemSnapshot in snapshot.children) {

                    val item = itemSnapshot.getValue(Item::class.java) ?: continue

                    val itemView = LayoutInflater.from(container.context)
                        .inflate(R.layout.item_template, container, false)

                    val imageView = itemView.findViewById<ImageView>(R.id.item_image)
                    val enderecoView = itemView.findViewById<TextView>(R.id.item_endereco)

                    enderecoView.text = item.endereco ?: "Endereço não informado"

                    when {
                        !item.imageUrl.isNullOrEmpty() -> {
                            Glide.with(container.context)
                                .load(item.imageUrl)
                                .into(imageView)
                        }

                        !item.base64Image.isNullOrEmpty() -> {
                            try {
                                val bytes = Base64.decode(item.base64Image, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                imageView.setImageBitmap(bitmap)
                            } catch (_: Exception) {
                            }
                        }

                        else -> {
                            imageView.setImageResource(R.drawable.meuicone)
                        }
                    }

                    container.addView(itemView)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(container.context, "Erro ao carregar itens", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
