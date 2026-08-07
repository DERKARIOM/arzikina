package com.arzikina.ne.presentation.profile

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentChangePasswordBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Ouvert DEPUIS Profil (voir `nav_graph.xml`) : la flèche retour y ramène toujours. */
@AndroidEntryPoint
class ChangePasswordFragment : Fragment(R.layout.fragment_change_password) {

    private val viewModel: ChangePasswordViewModel by viewModels()
    private var binding: FragmentChangePasswordBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentChangePasswordBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener {
            if (!viewModel.formState.value.isSubmitting) findNavController().navigateUp()
        }

        viewBinding.currentPasswordInput.doAfterTextChanged {
            viewModel.onCurrentPasswordChange(it?.toString().orEmpty())
        }
        viewBinding.newPasswordInput.doAfterTextChanged { viewModel.onNewPasswordChange(it?.toString().orEmpty()) }
        viewBinding.confirmPasswordInput.doAfterTextChanged {
            viewModel.onConfirmPasswordChange(it?.toString().orEmpty())
        }
        viewBinding.confirmPasswordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                viewModel.submit()
                true
            } else {
                false
            }
        }
        viewBinding.submitButton.setOnClickListener { viewModel.submit() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.formState.collect { state -> render(state) } }
                launch { viewModel.events.collect { event -> handleEvent(event) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun render(state: ChangePasswordFormState) {
        val binding = binding ?: return

        if (binding.currentPasswordInput.text?.toString() != state.currentPassword) {
            binding.currentPasswordInput.setText(state.currentPassword)
        }
        binding.currentPasswordLayout.error = state.currentPasswordError?.let { getString(it) }

        if (binding.newPasswordInput.text?.toString() != state.newPassword) {
            binding.newPasswordInput.setText(state.newPassword)
        }
        binding.newPasswordLayout.error = state.newPasswordError?.let { getString(it) }

        if (binding.confirmPasswordInput.text?.toString() != state.confirmPassword) {
            binding.confirmPasswordInput.setText(state.confirmPassword)
        }
        binding.confirmPasswordLayout.error = state.confirmPasswordError?.let { getString(it) }

        binding.submitButton.isEnabled = !state.isSubmitting
        binding.submitButton.text = if (state.isSubmitting) "" else getString(R.string.change_password_submit_action)
        binding.progressIndicator.visibility = if (state.isSubmitting) View.VISIBLE else View.GONE
    }

    private fun handleEvent(event: ChangePasswordEvent) {
        when (event) {
            ChangePasswordEvent.Changed -> {
                Toast.makeText(requireContext(), R.string.change_password_success_message, Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            is ChangePasswordEvent.ShowError -> {
                binding?.let { Snackbar.make(it.root, event.messageRes, Snackbar.LENGTH_LONG).show() }
            }
        }
    }
}
