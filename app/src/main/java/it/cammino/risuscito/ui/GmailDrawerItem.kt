package it.cammino.risuscito.ui

import android.content.Context
import android.view.View
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.util.themeDrawerItem
import it.cammino.risuscito.R

open class GmailDrawerItem : PrimaryDrawerItem() {

    override val type: Int
        get() = R.id.material_drawer_item_gmail_item

    override fun applyDrawerItemTheme(ctx: Context, view: View, selected_color: Int, animate: Boolean, shapeAppearanceModel: ShapeAppearanceModel) {
        themeDrawerItem(ctx, view, selected_color, animate, shapeAppearanceModel, R.dimen.gmail_material_drawer_item_background_padding_top_bottom, R.dimen.gmail_material_drawer_item_background_padding_start, R.dimen.gmail_material_drawer_item_background_padding_end)
    }

    override fun getShapeAppearanceModel(ctx: Context): ShapeAppearanceModel {
        val cornerRadius = ctx.resources.getDimensionPixelSize(R.dimen.gmail_material_drawer_item_corner_radius)
        return ShapeAppearanceModel.builder()
                .setTopRightCorner(CornerFamily.ROUNDED, cornerRadius.toFloat())
                .setBottomRightCorner(CornerFamily.ROUNDED, cornerRadius.toFloat())
                .build()
    }
}