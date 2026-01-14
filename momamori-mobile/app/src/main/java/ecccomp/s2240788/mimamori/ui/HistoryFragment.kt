package ecccomp.s2240788.mimamori.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import ecccomp.s2240788.mimamori.R
import ecccomp.s2240788.mimamori.data.SensorData
import ecccomp.s2240788.mimamori.viewmodel.MainViewModel

class HistoryFragment : Fragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HistoryAdapter
    private lateinit var filterAll: MaterialButton
    private lateinit var filterKiken: MaterialButton
    private lateinit var filterChui: MaterialButton
    private lateinit var filterSamui: MaterialButton
    private lateinit var filterAnzen: MaterialButton
    private var currentFilter: String = "all"
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)
        
        recyclerView = view.findViewById(R.id.historyRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = HistoryAdapter()
        recyclerView.adapter = adapter
        
        // Filter buttons
        filterAll = view.findViewById(R.id.filterAll)
        filterKiken = view.findViewById(R.id.filterKiken)
        filterChui = view.findViewById(R.id.filterChui)
        filterSamui = view.findViewById(R.id.filterSamui)
        filterAnzen = view.findViewById(R.id.filterAnzen)
        
        setupFilterButtons()
        observeData()
        
        return view
    }
    
    private fun setupFilterButtons() {
        filterAll.setOnClickListener { setFilter("all") }
        filterKiken.setOnClickListener { setFilter("KIKEN") }
        filterChui.setOnClickListener { setFilter("CHUI") }
        filterSamui.setOnClickListener { setFilter("SAMUI") }
        filterAnzen.setOnClickListener { setFilter("ANZEN") }
        
        setFilter("all")
    }
    
    private fun setFilter(status: String) {
        currentFilter = status
        updateFilterButtons()
        observeData()
    }
    
    private fun updateFilterButtons() {
        val buttons = listOf(filterAll, filterKiken, filterChui, filterSamui, filterAnzen)
        buttons.forEach { it.isSelected = false }
        
        when (currentFilter) {
            "all" -> filterAll.isSelected = true
            "KIKEN" -> filterKiken.isSelected = true
            "CHUI" -> filterChui.isSelected = true
            "SAMUI" -> filterSamui.isSelected = true
            "ANZEN" -> filterAnzen.isSelected = true
        }
    }
    
    private fun observeData() {
        val dataFlow = if (currentFilter == "all") {
            viewModel.allHistoryData
        } else {
            viewModel.getHistoryByStatus(currentFilter)
        }
        
        dataFlow.observe(viewLifecycleOwner, Observer { data ->
            adapter.updateList(data)
            view?.findViewById<View>(R.id.emptyStateLayout)?.visibility =
                if (data.isEmpty()) View.VISIBLE else View.GONE
        })
    }
    
    inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
        private var items: List<SensorData> = emptyList()
        
        fun updateList(newItems: List<SensorData>) {
            items = newItems
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }
        
        override fun getItemCount() = items.size
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val timeText = itemView.findViewById<android.widget.TextView>(R.id.historyTime)
            private val tempText = itemView.findViewById<android.widget.TextView>(R.id.historyTemp)
            private val humText = itemView.findViewById<android.widget.TextView>(R.id.historyHum)
            private val diText = itemView.findViewById<android.widget.TextView>(R.id.historyDi)
            private val statusText = itemView.findViewById<android.widget.TextView>(R.id.historyStatus)
            
            fun bind(data: SensorData) {
                val dateFormat = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", java.util.Locale.getDefault())
                timeText.text = dateFormat.format(java.util.Date(data.timestamp))
                
                tempText.text = "${String.format("%.1f", data.temperature)}°C"
                humText.text = "${String.format("%.1f", data.humidity)}%"
                diText.text = "DI: ${String.format("%.1f", data.discomfortIndex)}"
                
                val statusBadge = when (data.status) {
                    "ANZEN" -> "安全"
                    "CHUI" -> "注意"
                    "KIKEN" -> "危険"
                    "SAMUI" -> "寒い"
                    "REMOTE" -> "リモート"
                    else -> data.status
                }
                statusText.text = statusBadge
                
                // Set status badge color
                val colorRes = when (data.status) {
                    "ANZEN" -> R.color.status_anzen
                    "CHUI" -> R.color.status_chui
                    "KIKEN" -> R.color.status_kiken
                    "SAMUI" -> R.color.status_samui
                    "REMOTE" -> R.color.status_remote
                    else -> R.color.text_secondary
                }
                statusText.setTextColor(resources.getColor(colorRes, null))
            }
        }
    }
}
