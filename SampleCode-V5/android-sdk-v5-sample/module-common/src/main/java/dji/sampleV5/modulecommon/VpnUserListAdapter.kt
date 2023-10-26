package dji.sampleV5.modulecommon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_vpn_users.view.txtItemVpnUsersIp
import kotlinx.android.synthetic.main.item_vpn_users.view.txtItemVpnUsersName

class VpnUserListAdapter(
    private val items: List<VPNUser>,
    private val listener: Listener
) : RecyclerView.Adapter<VpnUserListAdapter.VpnUserListViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VpnUserListViewHolder =
        VpnUserListViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_vpn_users, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VpnUserListViewHolder, position: Int) {
        holder.bind(items[position])
        holder.itemView.setOnClickListener {
            listener.onUserClick(items[position])
        }
    }

    interface Listener {
        fun onUserClick(vpnUser: VPNUser)
    }

    class VpnUserListViewHolder(private val view: View) : RecyclerView.ViewHolder(view){

        fun bind(item: VPNUser) {
            view.txtItemVpnUsersName.text = item.alias
            view.txtItemVpnUsersIp.text = item.ip
        }
    }
}