package com.example.musicapp.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.musicapp.MainActivity
import com.example.musicapp.databinding.ItemTrackBinding
import com.example.musicapp.model.Track

class TrackAdapter(
    private val onItemClick: (Track) -> Unit,
    private val activity: MainActivity
) : RecyclerView.Adapter<TrackAdapter.TrackViewHolder>() {
    private var tracks: List<Track> = emptyList()

    fun setTracks(tracks: List<Track>) {
        this.tracks = tracks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrackViewHolder {
        val binding = ItemTrackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TrackViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: TrackViewHolder, position: Int) {
        val track = tracks[position]
        holder.bind(track, position + 1)
    }

    override fun getItemCount(): Int = tracks.size

    class TrackViewHolder(
        private val binding: ItemTrackBinding,
        private val onItemClick: (Track) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(track: Track, rank: Int) {
            binding.rankTextView.text = "#$rank"
            binding.trackTitleTextView.text = track.title
            binding.artistTextView.text = track.artist.name

            binding.root.setOnClickListener {
                if (track.isStreamable) {
                    onItemClick(track)
                }
            }
        }
    }
}