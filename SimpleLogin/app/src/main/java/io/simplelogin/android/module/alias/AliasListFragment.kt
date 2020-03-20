package io.simplelogin.android.module.alias

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import io.simplelogin.android.R
import io.simplelogin.android.databinding.FragmentAliasListBinding
import io.simplelogin.android.module.home.HomeActivity
import io.simplelogin.android.module.home.HomeSharedViewModel
import io.simplelogin.android.utils.SLApiService
import io.simplelogin.android.utils.SLSharedPreferences
import io.simplelogin.android.utils.baseclass.BaseFragment
import io.simplelogin.android.utils.enums.AliasFilterMode
import io.simplelogin.android.utils.enums.SLError
import io.simplelogin.android.utils.extension.copyToClipboard
import io.simplelogin.android.utils.extension.toastError
import io.simplelogin.android.utils.extension.toastShortly
import io.simplelogin.android.utils.extension.toastUpToDate
import io.simplelogin.android.utils.model.Alias
import java.lang.Exception

class AliasListFragment : BaseFragment(), Toolbar.OnMenuItemClickListener,
    TabLayout.OnTabSelectedListener, HomeActivity.OnBackPressed {
    private lateinit var binding: FragmentAliasListBinding
    private val homeSharedViewModel: HomeSharedViewModel by activityViewModels()
    private lateinit var adapter: AliasListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentAliasListBinding.inflate(inflater)
        binding.toolbar.setNavigationOnClickListener { showLeftMenu() }
        binding.toolbar.setOnMenuItemClickListener(this)
        binding.tabLayout.addOnTabSelectedListener(this)

        // ViewModel
        homeSharedViewModel.fetchAliases()
        homeSharedViewModel.eventUpdateAliases.observe(
            viewLifecycleOwner,
            Observer { updatedAliases ->
                if (updatedAliases) {
                    activity?.runOnUiThread {
                        adapter.submitList(homeSharedViewModel.filteredAliases)
                        // Better call adapter.notifyItemChanged(:position)
                        // but it is complicated and not so important with a small list
                        adapter.notifyDataSetChanged()
                    }
                    homeSharedViewModel.onEventUpdateAliasesComplete()

                    if (binding.swipeRefreshLayout.isRefreshing) {
                        binding.swipeRefreshLayout.isRefreshing = false
                        context?.toastUpToDate()
                    }
                }
            })

        homeSharedViewModel.error.observe(viewLifecycleOwner, Observer { error ->
            if (error != null) {
                context?.toastError(error)
                homeSharedViewModel.onHandleErrorComplete()
                binding.swipeRefreshLayout.isRefreshing = false
            }
        })

        // Reset tab selection state on configuration changed
        binding.tabLayout.getTabAt(homeSharedViewModel.aliasFilterMode.position)?.select()

        // RecyclerView
        adapter = AliasListAdapter(object : AliasListAdapter.ClickListener {
            val context = getContext() ?: throw Exception("Context is null")
            val apiKey = SLSharedPreferences.getApiKey(context) ?: context.toastError(SLError.NoApiKey)

            override fun onClick(alias: Alias) {
                Log.d("onClick", "${alias.id}")
            }

            override fun onSwitch(alias: Alias) {
                setLoading(true)
                SLApiService.toggleAlias(apiKey as String, alias.id) { enabled, error ->
                    activity?.runOnUiThread {
                        setLoading(false)

                        if (error != null) {
                            context.toastError(error)
                        } else if (enabled != null) {
                            alias.setEnabled(enabled)
                            homeSharedViewModel.filterAliases()
                        }
                    }
                }
            }

            override fun onCopy(alias: Alias) {
                val email = alias.email
                copyToClipboard(email, email)
                context.toastShortly("Copied \"$email\"")
            }

            override fun onSendEmail(alias: Alias) {
                findNavController().navigate(AliasListFragmentDirections.actionAliasFragmentToContactListFragment(alias))
            }

            override fun onDelete(alias: Alias, position: Int) {
                MaterialAlertDialogBuilder(context)
                    .setTitle("Delete \"${alias.email}\"?")
                    .setMessage("\uD83D\uDED1 People/apps who used to contact you via this alias cannot reach you any more. This operation is irreversible. Please confirm.")
                    .setNegativeButton("Delete") { _, _ ->
                        setLoading(true)
                        SLApiService.deleteAlias(apiKey as String, alias.id) { error ->
                            activity?.runOnUiThread {
                                setLoading(false)

                                if (error != null) {
                                    context.toastError(error)
                                } else {
                                    // Calling deleteAlias will also trigger filter and refresh the alias list
                                    homeSharedViewModel.deleteAlias(alias)
                                }
                            }
                        }
                    }
                    .setPositiveButton("Cancel", null)
                    .show()
            }
        })
        binding.recyclerView.adapter = adapter
        val linearLayoutManager = LinearLayoutManager(context)
        binding.recyclerView.layoutManager = linearLayoutManager

        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if ((linearLayoutManager.findLastCompletelyVisibleItemPosition() == homeSharedViewModel.filteredAliases.size - 1)
                    && homeSharedViewModel.moreAliasesToLoad
                ) {
                    homeSharedViewModel.fetchAliases()
                }
            }
        })

        binding.swipeRefreshLayout.setOnRefreshListener { homeSharedViewModel.refreshAliases() }
        setLoading(false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // On configuration change, trigger a recyclerView refresh by calling filter function
        if (adapter.itemCount == 0) {
            homeSharedViewModel.filterAliases()
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.rootConstraintLayout.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    // Toolbar.OnMenuItemClickListener
    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.searchMenuItem -> {
                Log.d("menu", "search")
            }

            R.id.randomMenuItem -> {
                Log.d("menu", "shuffle")
            }

            R.id.addMenuItem -> {
                Log.d("menu", "add")
            }
        }

        return true
    }

    // TabLayout.OnTabSelectedListener
    override fun onTabReselected(tab: TabLayout.Tab?) = Unit
    override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
    override fun onTabSelected(tab: TabLayout.Tab?) {
        when (tab?.position) {
            0 -> homeSharedViewModel.filterAliases(AliasFilterMode.ALL)
            1 -> homeSharedViewModel.filterAliases(AliasFilterMode.ACTIVE)
            2 -> homeSharedViewModel.filterAliases(AliasFilterMode.INACTIVE)
        }
    }

    // HomeActivity.OnBackPressed
    override fun onBackPressed() = showLeftMenu()
}