package com.swedaiaiwallpapersart.backgroundanimewallpaperaiphoto.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.ikame.android.sdk.IKSdkController
import com.ikame.android.sdk.data.dto.pub.IKAdError
import com.ikame.android.sdk.listener.pub.IKLoadDisplayAdViewListener
import com.ikame.android.sdk.listener.pub.IKShowWidgetAdListener
import com.ikame.android.sdk.widgets.IkmDisplayWidgetAdView
import com.ikame.android.sdk.widgets.IkmWidgetAdLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.swedai.ai.wallpapers.art.background.anime_wallpaper.aiphoto.R
import com.swedai.ai.wallpapers.art.background.anime_wallpaper.aiphoto.databinding.ListItemLiveWallpaperBinding
import com.swedai.ai.wallpapers.art.background.anime_wallpaper.aiphoto.databinding.StaggeredNativeLayoutBinding
import com.swedaiaiwallpapersart.backgroundanimewallpaperaiphoto.MainActivity
import com.swedaiaiwallpapersart.backgroundanimewallpaperaiphoto.interfaces.downloadCallback
import com.swedaiaiwallpapersart.backgroundanimewallpaperaiphoto.models.LiveWallpaperModel
import com.swedaiaiwallpapersart.backgroundanimewallpaperaiphoto.utils.AdConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveWallpaperAdapter(
    var arrayList: ArrayList<LiveWallpaperModel?>,
    var positionCallback: downloadCallback,
    private val myActivity: MainActivity
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var lastClickTime = 0L
    private val debounceThreshold = 2000L // 2 seconds
    private val VIEW_TYPE_CONTAINER1 = 0
    private val VIEW_TYPE_NATIVE_AD = 1
    private var lastAdShownPosition = -1

    var row = 0
    private val firstAdLineThreshold = if (AdConfig.firstAdLineViewListWallSRC != 0) AdConfig.firstAdLineViewListWallSRC else 4
    val firstline = firstAdLineThreshold * 3
    private val lineCount = if (AdConfig.lineCountViewListWallSRC != 0) AdConfig.lineCountViewListWallSRC else 5
    val lineC = lineCount * 3
    private val statusAd = AdConfig.adStatusViewListWallSRC
    private var coroutineScope: CoroutineScope? = null
    private var cachedAdLayout: IkmWidgetAdLayout? = null
    var nativeAdView: IkmDisplayWidgetAdView? = null
    private lateinit var context: Context

    fun setCoroutineScope(scope: CoroutineScope) {
        coroutineScope = scope
    }

    inner class ViewHolderContainer1(private val binding: ListItemLiveWallpaperBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(modela: ArrayList<LiveWallpaperModel?>, position: Int) {
            val model = modela[position]
            setAllData(
                model!!, adapterPosition, binding.loading, binding.wallpaper, binding.errorImage, binding.iap
            )
        }
    }

    inner class ViewHolderContainer3(private val binding: StaggeredNativeLayoutBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            loadAd(binding)
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        val layoutManager = recyclerView.layoutManager as GridLayoutManager
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (getItemViewType(position) == VIEW_TYPE_NATIVE_AD) {
                    layoutManager.spanCount // Make the ad span the full width
                } else {
                    1 // Regular item occupies 1 span
                }
            }
        }
    }

    override fun getItemCount() = arrayList.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        context = parent.context // Initialize context here
        return when (viewType) {
            VIEW_TYPE_CONTAINER1 -> {
                val binding = ListItemLiveWallpaperBinding.inflate(inflater, parent, false)
                ViewHolderContainer1(binding)
            }
            VIEW_TYPE_NATIVE_AD -> {
                val binding = StaggeredNativeLayoutBinding.inflate(inflater, parent, false)
                ViewHolderContainer3(binding)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    @SuppressLint("SuspiciousIndentation")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            VIEW_TYPE_CONTAINER1 -> {
                try {
                    val viewHolderContainer1 = holder as ViewHolderContainer1
                    viewHolderContainer1.bind(arrayList, position)
                } catch (e: NullPointerException) {
                    e.printStackTrace()
                }
            }
            VIEW_TYPE_NATIVE_AD -> {
                val viewHolderContainer3 = holder as ViewHolderContainer3
                viewHolderContainer3.bind()
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (AdConfig.ISPAIDUSER) {
            return VIEW_TYPE_CONTAINER1
        } else {
            row = position / 2
            Log.e("TAG", "getItemViewType: $row")
            val adPosition = firstAdLineThreshold + (lineCount * (row - firstAdLineThreshold) / lineCount)
            return when {
                (position + 1) == (firstline + 1) -> {
                    Log.e("TAG", "getItemViewType: $row")
                    lastAdShownPosition = row
                    VIEW_TYPE_NATIVE_AD
                }
                position + 1 > firstline + 1 && (position + 1 - (firstline + 1)) % (lineC + 1) == 0 -> VIEW_TYPE_NATIVE_AD
                else -> VIEW_TYPE_CONTAINER1
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setAllData(
        model: LiveWallpaperModel, position: Int, animationView: LottieAnimationView, wallpaperMainImage: ImageView, errorImg: ImageView, iap: ImageView
    ) {
        animationView.visibility = View.VISIBLE
        animationView.setAnimation(R.raw.loading_upload_image)

        iap.visibility = if (model.unlocked || AdConfig.ISPAIDUSER) {
            View.GONE
        } else {
            View.VISIBLE
        }

        Glide.with(context).load(AdConfig.BASE_URL_DATA + "/livewallpaper/" + model.thumnail_url)
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .thumbnail(0.1f)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean
                ): Boolean {
                    Log.d("onLoadFailed", "onLoadFailed: ")
                    animationView.setAnimation(R.raw.no_data_image_found)
                    animationView.visibility = View.VISIBLE
                    errorImg.visibility = View.VISIBLE
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean
                ): Boolean {
                    animationView.visibility = View.INVISIBLE
                    errorImg.visibility = View.GONE
                    Log.d("******loaded", "onResourceReady: ")
                    return false
                }
            }).into(wallpaperMainImage)

        wallpaperMainImage.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime >= debounceThreshold) {
                positionCallback.getPosition(position, model)
                lastClickTime = currentTime
            }
        }
    }

    private fun getAdLayout(context: Context): IkmWidgetAdLayout {
        if (cachedAdLayout == null) {
            val inflater = LayoutInflater.from(context)
            cachedAdLayout = inflater.inflate(R.layout.native_dialog_layout, null) as? IkmWidgetAdLayout
            cachedAdLayout?.apply {
                titleView = findViewById(R.id.custom_headline)
                bodyView = findViewById(R.id.custom_body)
                callToActionView = findViewById(R.id.custom_call_to_action)
                iconView = findViewById(R.id.custom_app_icon)
                mediaView = findViewById(R.id.custom_media)
            }
        }
        return cachedAdLayout!!
    }

    private fun loadAd(binding: StaggeredNativeLayoutBinding) {
        Log.e("TAG", "loadAd: inside method")
        coroutineScope?.launch(Dispatchers.Main) {
            val adLayout = getAdLayout(context) // Pass context instead of holder.itemView.context

            if (!binding.adsView.isAdLoaded) {
                IKSdkController.loadNativeDisplayAd("mainscr_live_tab_scroll", object : IKLoadDisplayAdViewListener {
                    override fun onAdLoaded(adObject: IkmDisplayWidgetAdView?) {
                        nativeAdView = adObject
                    }

                    override fun onAdLoadFail(error: IKAdError) {
                        Log.e("LIVE_WALL_SCREEN_ADAPTER", "onAdFailedToLoad: $error")
                    }
                })
            }

            withContext(Dispatchers.Main) {
                nativeAdView?.let { adView ->
                    binding.adsView.showWithDisplayAdView(
                        R.layout.shimmer_loading_native,
                        adLayout,
                        "mainscr_live_tab_scroll",
                        adView,
                        object : IKShowWidgetAdListener {
                            override fun onAdShowFail(error: IKAdError) {
                                Log.e("TAG", "onAdShowFail: native failed")
                                binding.adsView.visibility = if (statusAd == 0 || !isNetworkAvailable()) {
                                    View.GONE
                                } else {
                                    View.VISIBLE
                                }
                            }

                            override fun onAdShowed() {
                                binding.adsView.visibility = View.VISIBLE
                                Log.e("TAG", "onAdShowed: native loaded")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = myActivity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
        } else {
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo != null && networkInfo.isConnected
        }
    }

    fun updateMoreData(list: ArrayList<LiveWallpaperModel?>) {
        val startPosition = arrayList.size
        for (i in list) {
            if (!arrayList.contains(i)) {
                arrayList.add(i)
            }
        }
        notifyItemRangeInserted(startPosition, list.size)
    }

    fun getAllItems(): ArrayList<LiveWallpaperModel?> {
        return arrayList
    }

    fun addNewData() {
        arrayList.clear()
        notifyDataSetChanged()
    }

    fun updateData(list: ArrayList<LiveWallpaperModel?>) {
        arrayList.clear()
        arrayList.addAll(list)
        notifyDataSetChanged()
    }
}
