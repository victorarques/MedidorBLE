package com.medidorble.ui

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.medidorble.R
import com.medidorble.data.Project
import com.medidorble.data.ProjectRepository
import com.medidorble.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val projects get() = ProjectRepository.projects
    private lateinit var adapter: Adapter

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)

        adapter = Adapter()
        b.rvProjects.layoutManager = LinearLayoutManager(this)
        b.rvProjects.adapter = adapter

        b.fabNewProject.setOnClickListener { newProjectDialog() }
    }

    override fun onResume() { super.onResume(); adapter.notifyDataSetChanged() }

    private fun newProjectDialog() {
        val et = EditText(this).apply { hint = "Nom del projecte"; setPadding(48,16,48,16) }
        AlertDialog.Builder(this).setTitle("Nou projecte").setView(et)
            .setPositiveButton("Crear") { _, _ ->
                val name = et.text.toString().trim().ifBlank { "Projecte ${projects.size + 1}" }
                projects.add(Project(name = name))
                adapter.notifyItemInserted(projects.size - 1)
            }
            .setNegativeButton("Cancel·lar", null).show()
    }

    inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.tvProjectName)
            val info: TextView = v.findViewById(R.id.tvProjectInfo)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) =
            VH(LayoutInflater.from(p.context).inflate(R.layout.item_project, p, false))
        override fun getItemCount() = projects.size
        override fun onBindViewHolder(h: VH, pos: Int) {
            val p = projects[pos]
            h.name.text = p.name
            h.info.text = "${p.rooms.size} habitacions · ${"%.2f".format(p.totalArea())} m²"
            h.itemView.setOnClickListener {
                startActivity(Intent(this@MainActivity, RoomEditorActivity::class.java)
                    .putExtra("PROJECT_IDX", pos))
            }
            h.itemView.setOnLongClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Eliminar "${p.name}"?")
                    .setPositiveButton("Eliminar") { _, _ ->
                        projects.removeAt(pos); notifyItemRemoved(pos)
                    }.setNegativeButton("Cancel·lar", null).show()
                true
            }
        }
    }
}
