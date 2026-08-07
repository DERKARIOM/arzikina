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
import com.arzikina.ne.databinding.FragmentSecurityQuestionBinding
import com.arzikina.ne.domain.model.SecurityQuestion
import com.arzikina.ne.presentation.auth.displayTextRes
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Ouvert DEPUIS Profil (voir `nav_graph.xml`). Réutilise [displayTextRes]
 * (package `presentation.auth`, voir sa KDoc) : même mapping question -> texte
 * qu'à l'inscription, une seule source de vérité.
 */
@AndroidEntryPoint
class SecurityQuestionUpdateFragment : Fragment(R.layout.fragment_security_question) {

    private val viewModel: SecurityQuestionUpdateViewModel by viewModels()
    private var binding: FragmentSecurityQuestionBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentSecurityQuestionBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener {
            if (!viewModel.formState.value.isSubmitting) findNavController().navigateUp()
        }

        viewBinding.currentPasswordInput.doAfterTextChanged {
            viewModel.onCurrentPasswordChange(it?.toString().orEmpty())
        }
        viewBinding.securityAnswerInput.doAfterTextChanged {
            viewModel.onSecurityAnswerChange(it?.toString().orEmpty())
        }
        viewBinding.securityAnswerInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                viewModel.submit()
                true
            } else {
                false
            }
        }
        viewBinding.submitButton.setOnClickListener { viewModel.submit() }

        viewBinding.securityQuestionInput.setSimpleItems(
            SECURITY_QUESTIONS.map { getString(it.displayTextRes()) }.toTypedArray()
        )
        viewBinding.securityQuestionInput.setOnItemClickListener { _, _, position, _ ->
            viewModel.onSecurityQuestionChange(SECURITY_QUESTIONS[position])
        }

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

    private fun render(state: SecurityQuestionUpdateFormState) {
        val binding = binding ?: return

        if (binding.currentPasswordInput.text?.toString() != state.currentPassword) {
            binding.currentPasswordInput.setText(state.currentPassword)
        }
        binding.currentPasswordLayout.error = state.currentPasswordError?.let { getString(it) }

        val questionLabel = getString(state.securityQuestion.displayTextRes())
        if (binding.securityQuestionInput.text?.toString() != questionLabel) {
            binding.securityQuestionInput.setText(questionLabel, false)
        }

        if (binding.securityAnswerInput.text?.toString() != state.securityAnswer) {
            binding.securityAnswerInput.setText(state.securityAnswer)
        }
        binding.securityAnswerLayout.error = state.securityAnswerError?.let { getString(it) }

        binding.submitButton.isEnabled = !state.isSubmitting
        binding.submitButton.text =
            if (state.isSubmitting) "" else getString(R.string.update_security_question_submit_action)
        binding.progressIndicator.visibility = if (state.isSubmitting) View.VISIBLE else View.GONE
    }

    private fun handleEvent(event: SecurityQuestionUpdateEvent) {
        when (event) {
            SecurityQuestionUpdateEvent.Updated -> {
                Toast.makeText(
                    requireContext(), R.string.update_security_question_success_message, Toast.LENGTH_SHORT
                ).show()
                findNavController().navigateUp()
            }
            is SecurityQuestionUpdateEvent.ShowError -> {
                binding?.let { Snackbar.make(it.root, event.messageRes, Snackbar.LENGTH_LONG).show() }
            }
        }
    }

    private companion object {
        val SECURITY_QUESTIONS = SecurityQuestion.entries
    }
}
