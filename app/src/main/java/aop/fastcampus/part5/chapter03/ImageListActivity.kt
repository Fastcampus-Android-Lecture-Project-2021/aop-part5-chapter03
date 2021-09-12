package aop.fastcampus.part5.chapter03

import android.app.Activity
import android.content.Intent
import android.media.MediaScannerConnection
import android.media.MediaScannerConnection.OnScanCompletedListener
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.viewpager2.widget.ViewPager2
import aop.fastcampus.part5.chapter03.adapter.ImageViewPagerAdapter
import aop.fastcampus.part5.chapter03.databinding.ActivityImageListBinding
import aop.fastcampus.part5.chapter03.util.PathUtil
import java.io.File
import java.io.FileNotFoundException

class ImageListActivity : AppCompatActivity() {

    companion object {
        const val URI_LIST_KEY = "uriList"

        const val IMAGE_LIST_REQUEST_CODE = 100

        fun newIntent(activity: Activity, uriList: List<Uri>) =
            Intent(activity, ImageListActivity::class.java).apply {
                putExtra(URI_LIST_KEY, ArrayList<Uri>().apply { uriList.forEach { add(it) } })
            }
    }

    private lateinit var binding: ActivityImageListBinding
    private val uriList by lazy<List<Uri>> { intent.getParcelableArrayListExtra(URI_LIST_KEY)!! }
    private lateinit var imageViewPagerAdapter: ImageViewPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initViews()
    }

    private fun initViews() {
        setSupportActionBar(binding.toolbar)
        setupImageList(uriList)
    }

    private var currentUri: Uri? = null

    private fun setupImageList(uriList: List<Uri>) = with(binding) {
        if (::imageViewPagerAdapter.isInitialized.not()) {
            imageViewPagerAdapter = ImageViewPagerAdapter(uriList.toMutableList())
        }
        imageViewPager.adapter = imageViewPagerAdapter
        indicator.setViewPager(imageViewPager)
        imageViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                toolbar.title = if (imageViewPagerAdapter.uriList.isNotEmpty()) {
                    currentUri = imageViewPagerAdapter.uriList[position]
                    getString(R.string.images_page, position + 1, imageViewPagerAdapter.uriList.size)
                } else {
                    currentUri = null
                    ""
                }
            }
        })
        deleteButton.setOnClickListener {
            currentUri?.let { uri ->
                removeImage(uri)
            }
        }
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_OK, Intent().apply {
            putExtra(URI_LIST_KEY, ArrayList<Uri>().apply { imageViewPagerAdapter.uriList.forEach { add(it) } })
        })
        finish()
    }

    private fun removeImage(uri: Uri) {
        val file = File(PathUtil.getPath(this, uri) ?: throw FileNotFoundException())
        file.delete()
        val removedIndex = imageViewPagerAdapter.uriList.indexOf(uri)
        imageViewPagerAdapter.uriList.removeAt(removedIndex)
        imageViewPagerAdapter.notifyItemRemoved(removedIndex)
        binding.indicator.setViewPager(binding.imageViewPager)

        if (imageViewPagerAdapter.uriList.isNotEmpty()) {
            currentUri = if (removedIndex == 0) {
                imageViewPagerAdapter.uriList[removedIndex]
            } else {
                imageViewPagerAdapter.uriList[removedIndex - 1]
            }
        }

        MediaScannerConnection.scanFile(
            this, arrayOf(file.path), arrayOf(file.name)
        ) { _, _ ->
            contentResolver.delete(uri, null, null)
        }

        if (imageViewPagerAdapter.uriList.isEmpty()) {
            Toast.makeText(this, "삭제할 수 있는 이미지가 없습니다.", Toast.LENGTH_SHORT).show()
            onBackPressed()
        } else {
            binding.toolbar.title = getString(R.string.images_page, removedIndex + 1, imageViewPagerAdapter.uriList.size)
        }
    }

}
