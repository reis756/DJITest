package dji.sampleV5.modulecommon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_vpn_users.view.imgItemVpnUsersConnected
import kotlinx.android.synthetic.main.item_vpn_users.view.txtItemVpnUsersIp
import kotlinx.android.synthetic.main.item_vpn_users.view.txtItemVpnUsersName

class VpnUserListAdapter(
    private val listener: Listener
) : RecyclerView.Adapter<VpnUserListAdapter.VpnUserListViewHolder>() {

    private val items = mutableListOf<VPNUser>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VpnUserListViewHolder =
        VpnUserListViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_vpn_users, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VpnUserListViewHolder, position: Int) {
        holder.bind(items[position])
        holder.itemView.setOnClickListener {
            listener.onUserClick(items[position].copy(connected = true))
        }
    }

    fun setItems(list: List<VPNUser>) {
        val lastIndex = items.lastIndex
        notifyItemRangeRemoved(0, items.size)

        items.clear()
        items.addAll(list)

        notifyItemRangeInserted(lastIndex, list.size)
    }

    interface Listener {
        fun onUserClick(vpnUser: VPNUser)
    }

    class VpnUserListViewHolder(private val view: View) : RecyclerView.ViewHolder(view){

        fun bind(item: VPNUser) {
            view.imgItemVpnUsersConnected.setImageResource(
                if (item.connected == true) R.drawable.ic_vpn_connected else R.drawable.ic_vpn_disonnected
            )
            view.txtItemVpnUsersName.text = item.alias
            view.txtItemVpnUsersIp.text = item.ip
        }
    }
}