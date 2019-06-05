package it.cammino.risuscito

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.postDelayed
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.getInputField
import com.blogspot.atifsoftwares.animatoolib.Animatoo
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.adapters.FastItemAdapter
import com.mikepenz.fastadapter.drag.ItemTouchCallback
import com.mikepenz.fastadapter.swipe.SimpleSwipeCallback
import com.mikepenz.fastadapter.swipe_drag.SimpleSwipeDragCallback
import com.mikepenz.fastadapter.utils.DragDropUtil
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.colorInt
import com.mikepenz.iconics.paddingDp
import com.mikepenz.iconics.sizeDp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.utils.IconicsMenuInflaterUtil
import it.cammino.risuscito.database.RisuscitoDatabase
import it.cammino.risuscito.database.entities.ListaPers
import it.cammino.risuscito.dialogs.InputTextDialogFragment
import it.cammino.risuscito.dialogs.SimpleDialogFragment
import it.cammino.risuscito.items.SwipeableItem
import it.cammino.risuscito.items.swipeableItem
import it.cammino.risuscito.ui.SwipeDismissTouchListener
import it.cammino.risuscito.ui.ThemeableActivity
import it.cammino.risuscito.viewmodels.CreaListaViewModel
import kotlinx.android.synthetic.main.activity_crea_lista.*
import kotlinx.android.synthetic.main.hint_layout.*
import java.util.*
import kotlin.collections.ArrayList

class CreaListaActivity : ThemeableActivity(), InputTextDialogFragment.SimpleInputCallback, SimpleDialogFragment.SimpleCallback, ItemTouchCallback, SimpleSwipeCallback.ItemSwipeCallback {

    private lateinit var mViewModel: CreaListaViewModel
    private var celebrazione: ListaPersonalizzata? = null
    private var titoloLista: String? = null
    private var modifica: Boolean = false
    private var idModifica: Int = 0
    private var nomiCanti: ArrayList<String> = ArrayList()
    private var mAdapter: FastItemAdapter<SwipeableItem> = FastItemAdapter()
    private var mRegularFont: Typeface? = null
    private var elementi: ArrayList<SwipeableItem> = ArrayList()
    // drag & drop
    private var mTouchHelper: ItemTouchHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crea_lista)

        mViewModel = ViewModelProviders.of(this).get(CreaListaViewModel::class.java)

        mRegularFont = ResourcesCompat.getFont(this, R.font.googlesans_regular)

        risuscito_toolbar?.setBackgroundColor(themeUtils.primaryColor())
        setSupportActionBar(risuscito_toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        tabletToolbarBackground?.setBackgroundColor(themeUtils.primaryColor())
        action_title_bar.setBackgroundColor(themeUtils.primaryColor())

        val leaveBehindDrawable = IconicsDrawable(this)
                .icon(CommunityMaterial.Icon.cmd_delete)
                .colorInt(Color.WHITE)
                .sizeDp(24)
                .paddingDp(2)

        val touchCallback = SimpleSwipeDragCallback(
                this,
                this,
                leaveBehindDrawable,
                ItemTouchHelper.LEFT,
                ContextCompat.getColor(this, R.color.md_red_900))
                .withBackgroundSwipeRight(ContextCompat.getColor(this, R.color.md_red_900))
                .withLeaveBehindSwipeRight(leaveBehindDrawable)
        touchCallback.setIsDragEnabled(false)

        mTouchHelper = ItemTouchHelper(touchCallback) // Create ItemTouchHelper and pass with parameter the SimpleDragCallback

        mAdapter.add(elementi)
        mAdapter.onLongClickListener = { _: View?, _: IAdapter<SwipeableItem>, item: SwipeableItem, position: Int ->
            Log.d(TAG, "onItemLongClick: $position")
            mViewModel.positionToRename = position
            InputTextDialogFragment.Builder(
                    this, this, RENAME)
                    .title(R.string.posizione_rename)
                    .prefill(item.name.text.toString())
                    .positiveButton(R.string.aggiungi_rename)
                    .negativeButton(android.R.string.cancel)
                    .show()
            true
        }

        val llm = LinearLayoutManager(this)
        recycler_view?.layoutManager = llm

        recycler_view?.adapter = mAdapter
        recycler_view?.setHasFixedSize(true) // Size of RV will not change

        val insetDivider = DividerItemDecoration(this, llm.orientation)
        insetDivider.setDrawable(
                ContextCompat.getDrawable(
                        this, R.drawable.preference_list_divider_material)!!)
        recycler_view?.addItemDecoration(insetDivider)

        mTouchHelper?.attachToRecyclerView(recycler_view) // Attach ItemTouchHelper to RecyclerView

        SearchTask().execute()

        val icon = IconicsDrawable(this)
                .icon(CommunityMaterial.Icon2.cmd_plus)
                .colorInt(Color.WHITE)
                .sizeDp(24)
                .paddingDp(4)
        fab_crea_lista.setImageDrawable(icon)

        textTitleDescription.requestFocus()

        var iFragment = InputTextDialogFragment.findVisible(this, RENAME)
        iFragment?.setmCallback(this)
        iFragment = InputTextDialogFragment.findVisible(this, ADD_POSITION)
        iFragment?.setmCallback(this)
        val fragment = SimpleDialogFragment.findVisible(this, SAVE_LIST)
        fragment?.setmCallback(this)

        hint_text.setText(R.string.showcase_rename_desc)
        hint_text.append(System.getProperty("line.separator"))
        hint_text.append(getString(R.string.showcase_delete_desc))
        ViewCompat.setElevation(question_mark, 1f)
        main_hint_layout.setOnTouchListener(
                SwipeDismissTouchListener(
                        main_hint_layout, null,
                        object : SwipeDismissTouchListener.DismissCallbacks {
                            override fun canDismiss(token: Any?): Boolean {
                                return true
                            }

                            override fun onDismiss(view: View, token: Any?) {
                                main_hint_layout.visibility = View.GONE
                                PreferenceManager.getDefaultSharedPreferences(this@CreaListaActivity).edit { putBoolean(Utility.INTRO_CREALISTA_2, true) }
                            }
                        }))

        textfieldTitle.addTextChangedListener(
                object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {}

                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        collapsingToolbarLayout.title = s
                        mViewModel.tempTitle = s.toString()
                    }
                }
        )

        fab_crea_lista.setOnClickListener {
            InputTextDialogFragment.Builder(
                    this, this, ADD_POSITION)
                    .title(R.string.posizione_add_desc)
                    .positiveButton(R.string.aggiungi_confirm)
                    .negativeButton(R.string.cancel)
                    .show()
        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        IconicsMenuInflaterUtil.inflate(
                menuInflater, this, R.menu.crea_lista_menu, menu)
        super.onCreateOptionsMenu(menu)
        val mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        Log.d(
                TAG,
                "onCreateOptionsMenu - INTRO_CREALISTA: " + mSharedPrefs.getBoolean(Utility.INTRO_CREALISTA, false))
        if (!mSharedPrefs.getBoolean(Utility.INTRO_CREALISTA, false)) {
            Handler().postDelayed(1500) {
                playIntro()
            }
        }
        if (mAdapter.adapterItems.isEmpty() || mSharedPrefs.getBoolean(Utility.INTRO_CREALISTA_2, false))
            main_hint_layout.visibility = View.GONE
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_help -> {
                playIntro()
                if (mAdapter.adapterItems.isNotEmpty())
                    main_hint_layout.visibility = View.VISIBLE
                return true
            }
            R.id.action_save_list -> {
//                ioThread {
//                    if (saveList()) {
//                        setResult(Activity.RESULT_OK)
//                        finish()
//                        Animatoo.animateSlideDown(this)
//                    }
//                }
                SaveListTask().execute(textfieldTitle.text)
                return true
            }
            android.R.id.home -> {
                if (mAdapter.adapterItems.isNotEmpty()) {
                    SimpleDialogFragment.Builder(
                            this, this, SAVE_LIST)
                            .title(R.string.save_list_title)
                            .content(R.string.save_list_question)
                            .positiveButton(R.string.save_exit_confirm)
                            .negativeButton(R.string.discard_exit_confirm)
                            .show()
                    return true
                } else {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                    Animatoo.animateSlideDown(this)
                }
                return true
            }
        }
        return false
    }

    override fun onBackPressed() {
        Log.d(TAG, "onBackPressed: ")
        if (mAdapter.adapterItems.isNotEmpty()) {
            SimpleDialogFragment.Builder(this, this, SAVE_LIST)
                    .title(R.string.save_list_title)
                    .content(R.string.save_list_question)
                    .positiveButton(R.string.save_exit_confirm)
                    .negativeButton(R.string.discard_exit_confirm)
                    .show()
        } else {
            setResult(Activity.RESULT_CANCELED)
            finish()
            Animatoo.animateSlideDown(this)
        }
    }

//    private fun saveList(): Boolean {
//        celebrazione = ListaPersonalizzata()
//
//        if (!textfieldTitle.text.isNullOrBlank()) {
//            titoloLista = textfieldTitle.text.toString()
//        } else {
//            val toast = Toast.makeText(
//                    this, getString(R.string.no_title_edited), Toast.LENGTH_SHORT)
//            toast.show()
//        }
//
//        celebrazione?.name = titoloLista ?: ""
//        Log.d(TAG, "saveList - elementi.size(): " + mAdapter.adapterItems.size)
//        for (i in 0 until mAdapter.adapterItems.size) {
//            mAdapter.getItem(i)?.let {
//                if (celebrazione?.addPosizione(it.name.text.toString()) == -2) {
//                    Snackbar.make(
//                            main_content,
//                            R.string.lista_pers_piena,
//                            Snackbar.LENGTH_SHORT)
//                            .show()
//                    return false
//                }
//            }
//        }
//
//        if (celebrazione?.getNomePosizione(0).equals("", ignoreCase = true)) {
//            Snackbar.make(
//                    main_content, R.string.lista_pers_vuota, Snackbar.LENGTH_SHORT)
//                    .show()
//            return false
//        }
//
//        if (modifica) {
//            for (i in 0 until mAdapter.adapterItems.size) {
//                celebrazione?.addCanto(nomiCanti[i], i)
//            }
//        }
//
//        val mDao = RisuscitoDatabase.getInstance(this).listePersDao()
//        val listaToUpdate = ListaPers()
//        listaToUpdate.lista = celebrazione
//        listaToUpdate.titolo = titoloLista
//        if (modifica) {
//            listaToUpdate.id = idModifica
//            mDao.updateLista(listaToUpdate)
//        } else
//            mDao.insertLista(listaToUpdate)
//
//        return true
//    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle?) {
        super.onSaveInstanceState(savedInstanceState)
        mViewModel.dataDrag = mAdapter.adapterItems as ArrayList<SwipeableItem>
        if (modifica) mViewModel.data = nomiCanti
    }

    override fun onPositive(tag: String, dialog: MaterialDialog) {
        Log.d(TAG, "onPositive: $tag")
        when (tag) {
            RENAME -> {
                val mEditText = dialog.getInputField()
                val mElement = mAdapter.adapterItems[mViewModel.positionToRename]
                mElement.withName(mEditText.text.toString())
                mAdapter.notifyAdapterItemChanged(mViewModel.positionToRename)
            }
            ADD_POSITION -> {
                noElementsAdded.visibility = View.GONE
                val mEditText = dialog.getInputField()
                if (modifica) nomiCanti.add("")
                if (mAdapter.adapterItemCount == 0) {
                    elementi.clear()
                    elementi.add(
                            swipeableItem {
                                identifier = Utility.random(0, 5000).toLong()
                                touchHelper = mTouchHelper
                                withName(mEditText.text.toString())
                            }
                    )
                    mAdapter.add(elementi)
                    mAdapter.notifyItemInserted(0)
                } else {
                    val mSize = mAdapter.adapterItemCount
                    mAdapter.add(
                            swipeableItem {
                                identifier = Utility.random(0, 5000).toLong()
                                touchHelper = mTouchHelper
                                withName(mEditText.text.toString())
                            }
                    )
                    mAdapter.notifyAdapterItemInserted(mSize)
                }
                Log.d(TAG, "onPositive - elementi.size(): " + mAdapter.adapterItems.size)
                val mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
                Log.d(
                        TAG,
                        "onCreateOptionsMenu - INTRO_CREALISTA_2: " + mSharedPrefs.getBoolean(Utility.INTRO_CREALISTA_2, false))
                if (!mSharedPrefs.getBoolean(Utility.INTRO_CREALISTA_2, false)) {
                    main_hint_layout.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onNegative(tag: String, dialog: MaterialDialog) {}

    override fun onPositive(tag: String) {
        Log.d(TAG, "onPositive: $tag")
        when (tag) {
            SAVE_LIST ->
//                ioThread {
//                    if (saveList()) {
//                        setResult(Activity.RESULT_OK)
//                        finish()
//                        Animatoo.animateSlideDown(this)
//                    }
//                }
                SaveListTask().execute(textfieldTitle.text)
        }
    }

    override fun onNegative(tag: String) {
        Log.d(TAG, "onNegative: $tag")
        when (tag) {
            SAVE_LIST -> {
                setResult(Activity.RESULT_CANCELED)
                finish()
                Animatoo.animateSlideDown(this)
            }
        }
    }

    override fun itemTouchOnMove(oldPosition: Int, newPosition: Int): Boolean {
        if (modifica) Collections.swap(nomiCanti, oldPosition, newPosition) // change canto
        DragDropUtil.onMove(mAdapter.itemAdapter, oldPosition, newPosition)  // change position
        return true
    }

    override fun itemTouchDropped(oldPosition: Int, newPosition: Int) = Unit

    override fun itemSwiped(position: Int, direction: Int) {
        // -- Option 1: Direct action --
        // do something when swiped such as: select, remove, update, ...:
        // A) fastItemAdapter.select(position);
        // B) fastItemAdapter.remove(position);
        // C) update item, set "read" if an email etc

        // -- Option 2: Delayed action --
        val item = mAdapter.getItem(position) ?: return
        item.setSwipedDirection(direction)

        val deleteHandler = Handler {
            val itemOjb = it.obj as SwipeableItem

            itemOjb.setSwipedAction(null)
            val position12 = mAdapter.getAdapterPosition(itemOjb)
            if (position12 != RecyclerView.NO_POSITION) {
                //this sample uses a filter. If a filter is used we should use the methods provided by the filter (to make sure filter and normal state is updated)
                mAdapter.remove(position12)
                if (modifica) nomiCanti.removeAt(position12)
                if (mAdapter.adapterItemCount == 0) {
                    noElementsAdded.visibility = View.VISIBLE
                    main_hint_layout.visibility = View.GONE
                }
            }
            true
        }

        // This can vary depending on direction but remove & archive simulated here both results in
        // removal from list
        val message = Random().nextInt()
        deleteHandler.sendMessageDelayed(Message.obtain().apply { what = message; obj = item }, 2000)

        item.setSwipedAction(Runnable {
            deleteHandler.removeMessages(message)
            item.setSwipedDirection(0)
            val mPosition = mAdapter.getAdapterPosition(item)
            if (mPosition != RecyclerView.NO_POSITION)
                mAdapter.notifyItemChanged(mPosition)
        })

        mAdapter.notifyItemChanged(position)
    }

    private fun playIntro() {
        fab_crea_lista.show()
        TapTargetSequence(this)
                .continueOnCancel(true)
                .targets(
                        TapTarget.forView(
                                fab_crea_lista,
                                getString(R.string.add_position),
                                getString(R.string.showcase_add_pos_desc))
                                // All options below are optional
                                .outerCircleColorInt(
                                        themeUtils.primaryColor()) // Specify a color for the outer circle
                                .targetCircleColorInt(Color.WHITE) // Specify a color for the target circle
                                .textTypeface(mRegularFont) // Specify a typeface for the text
                                .titleTextColor(R.color.primary_text_default_material_dark)
                                .textColor(R.color.secondary_text_default_material_dark)
                                .tintTarget(false)
                                .id(1),
                        TapTarget.forToolbarMenuItem(
                                risuscito_toolbar,
                                R.id.action_save_list,
                                getString(R.string.list_save_exit),
                                getString(R.string.showcase_saveexit_desc))
                                // All options below are optional
                                .outerCircleColorInt(
                                        themeUtils.primaryColor()) // Specify a color for the outer circle
                                .targetCircleColorInt(Color.WHITE) // Specify a color for the target circle
                                .textTypeface(mRegularFont) // Specify a typeface for the text
                                .titleTextColor(R.color.primary_text_default_material_dark)
                                .textColor(R.color.secondary_text_default_material_dark)
                                .id(2),
                        TapTarget.forToolbarMenuItem(
                                risuscito_toolbar,
                                R.id.action_help,
                                getString(R.string.showcase_end_title),
                                getString(R.string.showcase_help_general))
                                // All options below are optional
                                .outerCircleColorInt(
                                        themeUtils.primaryColor()) // Specify a color for the outer circle
                                .targetCircleColorInt(Color.WHITE) // Specify a color for the target circle
                                .textTypeface(mRegularFont) // Specify a typeface for the text
                                .titleTextColor(R.color.primary_text_default_material_dark)
                                .textColor(R.color.secondary_text_default_material_dark)
                                .id(3))
                .listener(
                        object : TapTargetSequence.Listener { // The listener can listen for regular clicks, long clicks or cancels
                            override fun onSequenceFinish() {
                                Log.d(TAG, "onSequenceFinish: ")
                                PreferenceManager.getDefaultSharedPreferences(this@CreaListaActivity).edit { putBoolean(Utility.INTRO_CREALISTA, true) }
                            }

                            override fun onSequenceStep(tapTarget: TapTarget, b: Boolean) {}

                            override fun onSequenceCanceled(tapTarget: TapTarget) {
                                Log.d(TAG, "onSequenceCanceled: ")
                                PreferenceManager.getDefaultSharedPreferences(this@CreaListaActivity).edit { putBoolean(Utility.INTRO_CREALISTA, true) }
                            }
                        })
                .start()
    }

    @SuppressLint("StaticFieldLeak")
    private inner class SearchTask : AsyncTask<Void, Void, Void>() {

        override fun doInBackground(vararg savedInstanceState: Void): Void? {
            val bundle = this@CreaListaActivity.intent.extras
            modifica = bundle?.getBoolean("modifica") == true

            if (modifica) {
                idModifica = bundle?.getInt("idDaModif") ?: 0
                val mDao = RisuscitoDatabase.getInstance(this@CreaListaActivity).listePersDao()
                val lista = mDao.getListById(idModifica)
                titoloLista = lista?.titolo
                celebrazione = lista?.lista
            } else
                titoloLista = bundle?.getString("titolo")

            if (mViewModel.dataDrag != null) {
                elementi = mViewModel.dataDrag ?: ArrayList()
                for (elemento in elementi) elemento.touchHelper = mTouchHelper
            } else {
                if (modifica) {
                    celebrazione?.let {
                        for (i in 0 until it.numPosizioni) {
                            elementi.add(
                                    swipeableItem {
                                        identifier = Utility.random(0, 5000).toLong()
                                        touchHelper = mTouchHelper
                                        withName(it.getNomePosizione(i))
                                    }
                            )
                        }
                    }
                }
            }

            Log.d(TAG, "doInBackground: modifica $modifica")
            if (modifica) {
                if (mViewModel.data != null) {
                    nomiCanti = mViewModel.data ?: ArrayList()
                    Log.d(TAG, "doInBackground: nomiCanti size " + nomiCanti.size)
                } else {
                    if (modifica) {
                        celebrazione?.let {
                            for (i in 0 until it.numPosizioni) {
                                nomiCanti.add(it.getCantoPosizione(i))
                            }
                        }
                    }
                }
            }
            return null
        }

        override fun onPostExecute(result: Void?) {
            super.onPostExecute(result)
            mAdapter.set(elementi)
            if (mViewModel.tempTitle.isEmpty()) {
                textfieldTitle.setText(titoloLista)
                collapsingToolbarLayout.title = titoloLista
            } else {
                textfieldTitle.setText(mViewModel.tempTitle)
                collapsingToolbarLayout.title = mViewModel.tempTitle
            }
            if (elementi.size > 0) noElementsAdded.visibility = View.GONE
        }
    }

    @SuppressLint("StaticFieldLeak")
    private inner class SaveListTask : AsyncTask<Editable, Void, Int>() {

        override fun doInBackground(vararg titleText: Editable): Int {
            var result = 0
            celebrazione = ListaPersonalizzata()

            if (titleText[0].isNotBlank()) {
                titoloLista = titleText[0].toString()
            } else
                result += 100

            celebrazione?.name = titoloLista ?: ""
            Log.d(TAG, "saveList - elementi.size(): " + mAdapter.adapterItems.size)
            for (i in 0 until mAdapter.adapterItems.size) {
                mAdapter.getItem(i)?.let {
                    if (celebrazione?.addPosizione(it.name.text.toString()) == -2) {
                        return 1
                    }
                }
            }

            if (celebrazione?.getNomePosizione(0).equals("", ignoreCase = true))
                return 2

            if (modifica) {
                for (i in 0 until mAdapter.adapterItems.size) {
                    celebrazione?.addCanto(nomiCanti[i], i)
                }
            }

            val mDao = RisuscitoDatabase.getInstance(this@CreaListaActivity).listePersDao()
            val listaToUpdate = ListaPers()
            listaToUpdate.lista = celebrazione
            listaToUpdate.titolo = titoloLista
            if (modifica) {
                listaToUpdate.id = idModifica
                mDao.updateLista(listaToUpdate)
            } else
                mDao.insertLista(listaToUpdate)

            return result
        }

        override fun onPostExecute(result: Int) {
            super.onPostExecute(result)
            if (result == 100)
                Toast.makeText(this@CreaListaActivity, getString(R.string.no_title_edited), Toast.LENGTH_SHORT).show()
            when (result) {
                0, 100 -> {
                    setResult(Activity.RESULT_OK)
                    finish()
                    Animatoo.animateSlideDown(this@CreaListaActivity)
                }
                1 ->
                    Snackbar.make(
                            this@CreaListaActivity.main_content,
                            R.string.lista_pers_piena,
                            Snackbar.LENGTH_SHORT)
                            .show()
                2 ->
                    Snackbar.make(
                            this@CreaListaActivity.main_content, R.string.lista_pers_vuota, Snackbar.LENGTH_SHORT)
                            .show()

            }
        }
    }

    companion object {
        private val TAG = CreaListaActivity::class.java.canonicalName
        private const val RENAME = "RENAME"
        private const val ADD_POSITION = "ADD_POSITION"
        private const val SAVE_LIST = "SAVE_LIST"
    }
}
