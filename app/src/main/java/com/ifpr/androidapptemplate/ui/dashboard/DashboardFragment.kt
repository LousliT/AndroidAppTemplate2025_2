package com.ifpr.androidapptemplate.ui.dashboard

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.ifpr.androidapptemplate.baseclasses.Item
import com.ifpr.androidapptemplate.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private var imageUri: Uri? = null

    private lateinit var databaseReference: DatabaseReference
    private lateinit var auth: FirebaseAuth

    companion object {
        private const val PICK_IMAGE_REQUEST = 1
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val dashboardViewModel = ViewModelProvider(this).get(DashboardViewModel::class.java)

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val view = binding.root

        auth = FirebaseAuth.getInstance()

        dashboardViewModel.text.observe(viewLifecycleOwner) {
            binding.textDashboard.text = it
        }

        binding.buttonSelectImage.setOnClickListener {
            openFileChooser()
        }

        binding.salvarItemButton.setOnClickListener {
            salvarItem()
        }

        return view
    }

    private fun openFileChooser() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun salvarItem() {
        val endereco = binding.enderecoItemEditText.text.toString().trim()

        if (endereco.isEmpty() || imageUri == null) {
            Toast.makeText(requireContext(), "Por favor, preencha todos os campos", Toast.LENGTH_SHORT).show()
            return
        }

        uploadImageToBase64AndSave(endereco)
    }

    private fun uploadImageToBase64AndSave(endereco: String) {
        val inputStream = requireContext().contentResolver.openInputStream(imageUri!!)
        val bytes = inputStream?.readBytes()
        inputStream?.close()

        if (bytes == null) {
            Toast.makeText(requireContext(), "Erro ao ler a imagem", Toast.LENGTH_SHORT).show()
            return
        }

        val base64Image = Base64.encodeToString(bytes, Base64.DEFAULT)
        val item = Item(endereco = endereco, base64Image = base64Image)
        saveItemIntoDatabase(item)
    }

    private fun saveItemIntoDatabase(item: Item) {
        databaseReference = FirebaseDatabase.getInstance().getReference("itens")

        val userId = auth.uid
        if (userId == null) {
            Toast.makeText(requireContext(), "Usuário não logado", Toast.LENGTH_SHORT).show()
            return
        }

        val itemId = databaseReference.push().key
        if (itemId == null) {
            Toast.makeText(requireContext(), "Erro ao gerar ID do item", Toast.LENGTH_SHORT).show()
            return
        }

        databaseReference.child(userId).child(itemId).setValue(item)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Item cadastrado com sucesso!", Toast.LENGTH_SHORT).show()
                binding.enderecoItemEditText.text?.clear()
                binding.imageItem.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Falha ao cadastrar o item: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST &&
            resultCode == Activity.RESULT_OK &&
            data != null && data.data != null) {

            imageUri = data.data
            Glide.with(this).load(imageUri).into(binding.imageItem)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
