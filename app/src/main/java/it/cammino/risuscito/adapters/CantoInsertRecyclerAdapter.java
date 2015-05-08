package it.cammino.risuscito.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import it.cammino.risuscito.R;
import it.cammino.risuscito.Utility;
import it.cammino.risuscito.objects.CantoInsert;

/**
 * Created by marcello.battain on 12/01/2015.
 */
public class CantoInsertRecyclerAdapter extends RecyclerView.Adapter {

    private List<CantoInsert> dataItems;
    private View.OnClickListener clickListener;
    private View.OnClickListener seeClickListener;

    // Adapter constructor 1
    public CantoInsertRecyclerAdapter(List<CantoInsert> dataItems
            , View.OnClickListener clickListener) {

        this.dataItems = dataItems;
        this.clickListener = clickListener;
        this.seeClickListener = null;
    }

    // Adapter constructor 2
    public CantoInsertRecyclerAdapter(List<CantoInsert> dataItems
            , View.OnClickListener clickListener
            , View.OnClickListener seeClickListener) {

        this.dataItems = dataItems;
        this.clickListener = clickListener;
        this.seeClickListener = seeClickListener;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {

        View layoutView = LayoutInflater
                .from(viewGroup.getContext())
                .inflate(R.layout.row_item_to_insert, viewGroup, false);
        return new CantoViewHolder(layoutView, clickListener, seeClickListener);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {

        CantoInsert dataItem = dataItems.get(position);

        // Casting the viewHolder to MyViewHolder so I could interact with the views
        CantoViewHolder cantoHolder = (CantoViewHolder) viewHolder;
        cantoHolder.cantoTitle.setText(dataItem.getTitolo());
        cantoHolder.cantoPage.setText(String.valueOf(dataItem.getPagina()));
        cantoHolder.idCanto.setText(String.valueOf(dataItem.getIdCanto()));
        cantoHolder.sourceCanto.setText(dataItem.getSource());

        if (dataItem.getColore().equalsIgnoreCase(Utility.GIALLO))
            cantoHolder.cantoPage.setBackgroundResource(R.drawable.bkg_round_yellow);
        if (dataItem.getColore().equalsIgnoreCase(Utility.GRIGIO))
            cantoHolder.cantoPage.setBackgroundResource(R.drawable.bkg_round_grey);
        if (dataItem.getColore().equalsIgnoreCase(Utility.VERDE))
            cantoHolder.cantoPage.setBackgroundResource(R.drawable.bkg_round_green);
        if (dataItem.getColore().equalsIgnoreCase(Utility.AZZURRO))
            cantoHolder.cantoPage.setBackgroundResource(R.drawable.bkg_round_blue);
        if (dataItem.getColore().equalsIgnoreCase(Utility.BIANCO))
            cantoHolder.cantoPage.setBackgroundResource(R.drawable.bkg_round_white);

    }

    @Override
    public int getItemCount() {
        return dataItems.size();
    }

    public static class CantoViewHolder extends RecyclerView.ViewHolder {

        public TextView cantoTitle;
        public TextView cantoPage;
        public TextView idCanto;
        public TextView sourceCanto;
//        public View seeCanto;

        public CantoViewHolder(View itemView
                , View.OnClickListener onClickListener
                , View.OnClickListener seeOnClickListener) {
            super(itemView);
            cantoTitle = (TextView) itemView.findViewById(R.id.text_title);
            cantoPage = (TextView) itemView.findViewById(R.id.text_page);
            idCanto = (TextView) itemView.findViewById(R.id.text_id_canto);
            sourceCanto = (TextView) itemView.findViewById(R.id.text_source_canto);
            View seeCanto = itemView.findViewById(R.id.preview);

            if (onClickListener != null)
                itemView.setOnClickListener(onClickListener);
            if (seeOnClickListener != null)
                seeCanto.setOnClickListener(seeOnClickListener);
        }

    }
}