package com.naniger.arzikina.presentation.shop.stock

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentShopStockHistoryBinding
import com.naniger.arzikina.domain.shop.ProductUnit
import com.naniger.arzikina.presentation.shop.ShopFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Historique des mouvements de stock d'un produit, chargé par pages au défilement. */
@AndroidEntryPoint
class ShopStockHistoryFragment : Fragment(R.layout.fragment_shop_stock_history) {

    private val viewModel: ShopStockHistoryViewModel by viewModels()
    private var binding: FragmentShopStockHistoryBinding? = null
    private val adapter = ShopStockHistoryAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentShopStockHistoryBinding.bind(view)
        binding = b

        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        val layoutManager = LinearLayoutManager(requireContext())
        b.movementsList.layoutManager = layoutManager
        b.movementsList.adapter = adapter
        b.movementsList.itemAnimator = null
        b.movementsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - PREFETCH_DISTANCE) {
                    viewModel.onEndReached()
                }
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.uiState.collect(::render) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding?.movementsList?.adapter = null
        binding = null
    }

    private fun render(state: StockHistoryUiState) {
        val b = binding ?: return
        val loaded = state as? StockHistoryUiState.Loaded
        val product = loaded?.product
        val unit = product?.unit ?: ProductUnit.PIECE
        adapter.submit(loaded?.items.orEmpty(), unit, loaded?.currencyCode.orEmpty())

        b.loading.visibility = if (state is StockHistoryUiState.Loading) View.VISIBLE else View.GONE
        val empty = loaded != null && loaded.items.isEmpty()
        b.messageText.visibility = if (state is StockHistoryUiState.Error || empty) View.VISIBLE else View.GONE
        b.messageText.setText(if (empty) R.string.shop_stock_history_empty else R.string.shop_stock_history_error)
        b.movementsList.visibility = if (loaded != null && !empty) View.VISIBLE else View.GONE
        b.productName.text = product?.let {
            "${it.name} · ${ShopFormat.quantity(requireContext(), it.stockMilli, it.unit)}"
        }.orEmpty()
    }

    private companion object {
        /** Charge la page suivante quand il reste moins de 10 lignes avant la fin. */
        const val PREFETCH_DISTANCE = 10
    }
}
