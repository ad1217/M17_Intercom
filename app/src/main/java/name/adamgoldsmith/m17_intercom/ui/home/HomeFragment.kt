package name.adamgoldsmith.m17_intercom.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.agold.intercom.data.Channel
import com.agold.intercom.module.ExtModuleManager
import kotlinx.coroutines.launch
import name.adamgoldsmith.m17_intercom.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)
        val extModuleManager = ExtModuleManager.getInstance(requireContext())

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        binding.buttonStart.setOnClickListener { view ->
            extModuleManager.start()

            Log.d("m17_intercom", "Started")
        }

        binding.buttonSetChannel.setOnClickListener { view ->
            val channel = Channel(
                type = 1,
                power = 0,
                name = "Test",
                sendFreq = 446625000,
                recvFreq = 446625000,
                band = 0,
                squelchLevel = 2,
                recvSubAudioType = 1,
                sendSubAudioType = 1,
            )
            lifecycleScope.launch {
                extModuleManager.setChannel(channel)
            }
        }

        binding.buttonStop.setOnClickListener { view ->
            val extModuleManager = ExtModuleManager.getInstance(requireContext())
            extModuleManager.stop()
        }

        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        extModuleManager.mcuState.asLiveData().observe(viewLifecycleOwner) {
            binding.mcuText.text = it.name
        }
        extModuleManager.channelState.asLiveData().observe(viewLifecycleOwner) {
            binding.channelText.text = it.name
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}