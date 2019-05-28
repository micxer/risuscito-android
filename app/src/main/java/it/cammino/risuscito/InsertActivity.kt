package it.cammino.risuscito

import android.content.Intent
import android.os.AsyncTask
import android.os.AsyncTask.Status
import android.os.Bundle
import android.os.SystemClock
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blogspot.atifsoftwares.animatoolib.Animatoo
import com.crashlytics.android.Crashlytics
import com.github.zawadz88.materialpopupmenu.ViewBoundCallback
import com.github.zawadz88.materialpopupmenu.popupMenu
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.IAdapter
import com.mikepenz.fastadapter.adapters.FastItemAdapter
import com.mikepenz.fastadapter.listeners.ClickEventHook
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.colorRes
import com.mikepenz.iconics.paddingDp
import com.mikepenz.iconics.sizeDp
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import it.cammino.risuscito.database.RisuscitoDatabase
import it.cammino.risuscito.database.entities.ListaPers
import it.cammino.risuscito.items.InsertItem
import it.cammino.risuscito.ui.ThemeableActivity
import it.cammino.risuscito.ui.makeClearableEditText
import it.cammino.risuscito.utils.ListeUtils
import it.cammino.risuscito.utils.ioThread
import it.cammino.risuscito.viewmodels.SimpleIndexViewModel
import it.cammino.risuscito.viewmodels.ViewModelWithArgumentsFactory
import kotlinx.android.synthetic.main.risuscito_toolbar_noelevation.*
import kotlinx.android.synthetic.main.search_layout.*
import kotlinx.android.synthetic.main.tinted_progressbar.*
import kotlinx.android.synthetic.main.view_custom_item_checkable.view.*
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.lang.ref.WeakReference

class InsertActivity : ThemeableActivity() {

    internal val cantoAdapter: FastItemAdapter<InsertItem> = FastItemAdapter()
    private lateinit var aTexts: Array<Array<String?>>

    private var listePersonalizzate: List<ListaPers>? = null
    private var mLUtils: LUtils? = null
    private var searchTask: SearchTask? = null
    private var mLastClickTime: Long = 0
    private var listaPredefinita: Int = 0
    private var idLista: Int = 0
    private var listPosition: Int = 0

    private lateinit var mViewModel: SimpleIndexViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_insert_search)

        risuscito_toolbar.setBackgroundColor(themeUtils.primaryColor())
        risuscito_toolbar.title = getString(R.string.title_activity_inserisci_titolo)
        setSupportActionBar(risuscito_toolbar)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val bundle = intent.extras
        listaPredefinita = bundle!!.getInt("fromAdd")
        idLista = bundle.getInt("idLista")
        listPosition = bundle.getInt("position")

        val args = Bundle().apply { putInt("tipoLista", 3) }
        mViewModel = ViewModelProviders.of(this, ViewModelWithArgumentsFactory(application, args)).get(SimpleIndexViewModel::class.java)
        if (savedInstanceState == null) {
            val pref = PreferenceManager.getDefaultSharedPreferences(this)
            val currentItem = Integer.parseInt(pref.getString(Utility.DEFAULT_SEARCH, "0")!!)
            mViewModel.advancedSearch = currentItem != 0
        }

        try {
            val inputStream: InputStream = when (ThemeableActivity.getSystemLocalWrapper(resources.configuration)
                    .language) {
                "uk" -> assets.open("fileout_uk.xml")
                "en" -> assets.open("fileout_en.xml")
                else -> assets.open("fileout_new.xml")
            }
            aTexts = CantiXmlParser().parse(inputStream)
            inputStream.close()
        } catch (e: XmlPullParserException) {
            Log.e(TAG, "Error:", e)
            Crashlytics.logException(e)
        } catch (e: IOException) {
            Log.e(TAG, "Error:", e)
            Crashlytics.logException(e)
        }

        mLUtils = LUtils.getInstance(this)

        ioThread { listePersonalizzate = RisuscitoDatabase.getInstance(this).listePersDao().all }

        ricerca_subtitle.text = if (mViewModel.advancedSearch) getString(R.string.advanced_search_subtitle) else getString(R.string.fast_search_subtitle)

        cantoAdapter.onClickListener = { _: View?, _: IAdapter<InsertItem>, item: InsertItem, _: Int ->
            var consume = false
            if (SystemClock.elapsedRealtime() - mLastClickTime >= Utility.CLICK_DELAY) {
                mLastClickTime = SystemClock.elapsedRealtime()
                if (listaPredefinita == 1) {
                    ListeUtils.addToListaDupAndFinish(this, idLista, listPosition, item.id)
                } else {
                    ListeUtils.updateListaPersonalizzataAndFinish(this, idLista, item.id, listPosition)
                }
                consume = true
            }
            consume
        }

        cantoAdapter.addEventHook(object : ClickEventHook<InsertItem>() {
            override fun onBind(viewHolder: RecyclerView.ViewHolder): View? {
                return (viewHolder as? InsertItem.ViewHolder)?.mPreview
            }

            override fun onClick(v: View, position: Int, fastAdapter: FastAdapter<InsertItem>, item: InsertItem) {
                if (SystemClock.elapsedRealtime() - mLastClickTime < Utility.CLICK_DELAY) return
                mLastClickTime = SystemClock.elapsedRealtime()
                val intent = Intent(applicationContext, PaginaRenderActivity::class.java)
                intent.putExtras(bundleOf("pagina" to item.source!!.getText(this@InsertActivity), "idCanto" to item.id))
                mLUtils!!.startActivityWithTransition(intent)
            }
        })

        cantoAdapter.setHasStableIds(true)

        matchedList.adapter = cantoAdapter
        val llm = if (mLUtils!!.isGridLayout)
            GridLayoutManager(this, if (mLUtils!!.hasThreeColumns) 3 else 2)
        else
            LinearLayoutManager(this)
        matchedList.layoutManager = llm
        matchedList.setHasFixedSize(true)
        val insetDivider = DividerItemDecoration(this, llm.orientation)
        insetDivider.setDrawable(
                ContextCompat.getDrawable(this, R.drawable.material_inset_divider)!!)
        matchedList.addItemDecoration(insetDivider)

        val icon = IconicsDrawable(this)
                .icon(CommunityMaterial.Icon.cmd_close_circle)
                .colorRes(R.color.text_color_secondary)
                .sizeDp(32)
                .paddingDp(8)
        icon.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
        textfieldRicerca.makeClearableEditText(null, null, icon)

        textfieldRicerca.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == EditorInfo.IME_ACTION_DONE) {
                // to hide soft keyboard
                (ContextCompat.getSystemService(this, InputMethodManager::class.java) as InputMethodManager)
                        .hideSoftInputFromWindow(textfieldRicerca.windowToken, 0)
                return@setOnKeyListener true
            }
            return@setOnKeyListener false
        }

        textfieldRicerca.addTextChangedListener(
                object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {}

                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        ricercaStringa(s.toString())
                    }
                }
        )

        more_options.setOnClickListener {
            val popupMenu = popupMenu {
                dropdownGravity = Gravity.END
                section {
                    customItem {
                        layoutResId = R.layout.view_custom_item_checkable
                        dismissOnSelect = false
                        viewBoundCallback = ViewBoundCallback { view ->
                            view.customItemCheckbox.isChecked = mViewModel.advancedSearch
                            view.customItemCheckbox.setOnCheckedChangeListener { _, isChecked ->
                                mViewModel.advancedSearch = isChecked
                                ricerca_subtitle.text = if (mViewModel.advancedSearch) getString(R.string.advanced_search_subtitle) else getString(R.string.fast_search_subtitle)
                                ricercaStringa(textfieldRicerca.text.toString())
                                dismissPopup()
                            }
                        }
                    }
                    customItem {
                        layoutResId = R.layout.view_custom_item_checkable
                        dismissOnSelect = false
                        viewBoundCallback = ViewBoundCallback { view ->
                            view.customItemText.text = getString(R.string.consegnati_only)
                            view.customItemCheckbox.isChecked = mViewModel.consegnatiOnly
                            view.customItemCheckbox.setOnCheckedChangeListener { _, isChecked ->
                                mViewModel.consegnatiOnly = isChecked
                                ricercaStringa(textfieldRicerca.text.toString())
                                dismissPopup()
                            }
                        }
                    }
                }
            }
            popupMenu.show(this, it)
        }

        subscribeUiFavorites()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                setResult(CustomLists.RESULT_CANCELED)
                finish()
                Animatoo.animateShrink(this)
                return true
            }
        }
        return false
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        if (searchTask != null && searchTask!!.status == Status.RUNNING) searchTask!!.cancel(true)
        super.onDestroy()
    }

    override fun onBackPressed() {
        Log.d(TAG, "onBackPressed: ")
        setResult(CustomLists.RESULT_CANCELED)
        finish()
        Animatoo.animateShrink(this)
    }

    private fun ricercaStringa(s: String) {
        // abilita il pulsante solo se la stringa ha più di 3 caratteri, senza contare gli spazi
        if (s.trim { it <= ' ' }.length >= 3) {
            if (searchTask != null && searchTask!!.status == Status.RUNNING) searchTask!!.cancel(true)
            searchTask = SearchTask(this)
            searchTask!!.execute(textfieldRicerca.text.toString(), mViewModel.advancedSearch, mViewModel.consegnatiOnly)
        } else {
            if (s.isEmpty()) {
                if (searchTask != null && searchTask!!.status == Status.RUNNING)
                    searchTask!!.cancel(true)
                search_no_results.visibility = View.GONE
                cantoAdapter.clear()
                search_progress.visibility = View.INVISIBLE
            }
        }
    }

    private class SearchTask internal constructor(fragment: InsertActivity) : AsyncTask<Any, Void, ArrayList<InsertItem>>() {

        private val fragmentReference: WeakReference<InsertActivity> = WeakReference(fragment)

        override fun doInBackground(vararg sSearchText: Any): ArrayList<InsertItem> {

            val titoliResult = ArrayList<InsertItem>()

            Log.d(TAG, "STRINGA: " + sSearchText[0])
            Log.d(TAG, "ADVANCED: " + sSearchText[1])
            Log.d(TAG, "CONSEGNATI ONLY: " + sSearchText[2])
            val s = sSearchText[0] as String
            val advanced = sSearchText[1] as Boolean
            val consegnatiOnly = sSearchText[2] as Boolean

            if (advanced) {
                val words = s.split("\\W".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                var text: String

                for (aText in fragmentReference.get()!!.aTexts) {
                    if (isCancelled) return titoliResult

                    if (aText[0] == null || aText[0].equals("", ignoreCase = true)) break

                    var found = true
                    for (word in words) {
                        if (isCancelled) return titoliResult

                        if (word.trim { it <= ' ' }.length > 1) {
                            text = word.trim { it <= ' ' }
                            text = text.toLowerCase(
                                    getSystemLocalWrapper(
                                            fragmentReference.get()!!.resources.configuration))
                            text = Utility.removeAccents(text)

                            if (!aText[1]!!.contains(text)) found = false
                        }
                    }

                    if (found) {
                        Log.d(TAG, "aText[0]: ${aText[0]}")
                        fragmentReference.get()!!.mViewModel.titoliInsert.sortedBy { it.title!!.getText(fragmentReference.get()!!) }
                                .filter { it.undecodedSource == aText[0]!! && (!consegnatiOnly || it.consegnato == 1) }
                                .forEach {
                                    if (isCancelled) return titoliResult
                                    titoliResult.add(it)
                                }

                    }
                }
            } else {
                val stringa = Utility.removeAccents(s).toLowerCase()
                Log.d(TAG, "onTextChanged: stringa $stringa")
                fragmentReference.get()!!.mViewModel.titoliInsert.sortedBy { it.title!!.getText(fragmentReference.get()!!) }
                        .filter { Utility.removeAccents(it.title!!.getText(fragmentReference.get()!!)).toLowerCase().contains(stringa) && (!consegnatiOnly || it.consegnato == 1) }
                        .forEach {
                            if (isCancelled) return titoliResult
                            titoliResult.add(it.withFilter(stringa))
                        }
            }
            return titoliResult
        }

        override fun onPreExecute() {
            super.onPreExecute()
            if (isCancelled) return
            fragmentReference.get()?.search_no_results?.visibility = View.GONE
            fragmentReference.get()?.search_progress?.visibility = View.VISIBLE
        }

        override fun onPostExecute(titoliResult: ArrayList<InsertItem>) {
            super.onPostExecute(titoliResult)
            if (isCancelled) return
            fragmentReference.get()?.cantoAdapter?.set(titoliResult)
            fragmentReference.get()?.search_progress?.visibility = View.INVISIBLE
            fragmentReference.get()?.search_no_results?.visibility = if (fragmentReference.get()?.cantoAdapter?.adapterItemCount == 0)
                View.VISIBLE
            else
                View.GONE
        }
    }

    private fun subscribeUiFavorites() {
        mViewModel
                .insertItemsResult!!
                .observe(
                        this,
                        Observer<List<InsertItem>> { canti ->
                            if (canti != null) {
                                mViewModel.titoliInsert = canti.sortedBy { it.title!!.getText(this) }
                            }
                        })
    }

    companion object {
        private val TAG = InsertActivity::class.java.canonicalName
    }
}