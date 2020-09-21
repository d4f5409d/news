package co.appreactor.nextcloud.news.entry

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.BulletSpan
import android.text.style.ImageSpan
import android.text.style.QuoteSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import co.appreactor.nextcloud.news.R
import co.appreactor.nextcloud.news.common.showDialog
import co.appreactor.nextcloud.news.db.Entry
import com.google.android.material.textview.MaterialTextView
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_entry.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.viewmodel.ext.android.viewModel
import timber.log.Timber

class EntryFragment : Fragment() {

    private val args: EntryFragmentArgs by navArgs()

    private val model: EntryFragmentModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.fragment_entry,
            container,
            false
        )
    }

    @SuppressLint("NewApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        progress.isVisible = true

        lifecycleScope.launchWhenResumed {
            val entry = model.getEntry(args.entryId)

            if (entry == null) {
                showDialog(R.string.error, getString(R.string.cannot_find_entry_with_id_s, args.entryId)) {
                    findNavController().popBackStack()
                }

                return@launchWhenResumed
            }

            viewEntry(entry)
        }
    }

    private suspend fun viewEntry(entry: Entry) {
        model.markAsViewed(entry)

        toolbar.title = model.getFeed(entry.feedId)?.title ?: getString(R.string.unknown_feed)
        title.text = entry.title
        date.text = model.getDate(entry)

        fillBody(entry)

        progress.isVisible = false

        lifecycleScope.launchWhenResumed {
            model.getBookmarked(entry).collect { bookmarked ->
                val menuItem = toolbar.menu.findItem(R.id.toggleBookmarked)

                if (bookmarked) {
                    menuItem.setIcon(R.drawable.ic_baseline_bookmark_24)
                    menuItem.setTitle(R.string.remove_bookmark)
                } else {
                    menuItem.setIcon(R.drawable.ic_baseline_bookmark_border_24)
                    menuItem.setTitle(R.string.bookmark)
                }
            }
        }

        toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.toggleBookmarked) {
                lifecycleScope.launchWhenResumed {
                    model.toggleBookmarked(args.entryId)
                }
            }

            true
        }

        fab.setOnClickListener {
            lifecycleScope.launch {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(entry.link)
                startActivity(intent)
            }
        }
    }

    private fun fillBody(entry: Entry) {
        val body = HtmlCompat.fromHtml(
            entry.summary,
            HtmlCompat.FROM_HTML_MODE_LEGACY,
            null,
            null
        ) as SpannableStringBuilder

        val chunks = mutableListOf<Any>()
        var lastChunkEnd = 0

        val spans = body.getSpans(0, body.length - 1, Any::class.java)

        spans.forEach {
            when (it) {
                is ImageSpan -> {
                    chunks += body.subSequence(lastChunkEnd, body.getSpanStart(it)) as SpannableStringBuilder
                    chunks += it
                    lastChunkEnd = body.getSpanEnd(it)
                }
            }
        }

        chunks += body.subSequence(lastChunkEnd, body.length - 1) as SpannableStringBuilder

        chunks.forEachIndexed { chunkIndex, chunk ->
            when (chunk) {
                is SpannableStringBuilder -> {
                    val textView = MaterialTextView(requireContext())
                    TextViewCompat.setTextAppearance(textView, R.style.TextAppearance_MaterialComponents_Body1)
                    textView.setLineSpacing(0f, 1.2f)

                    textView.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )

                    chunk.applyStyle(textView)

                    while (chunk.contains("\u00A0")) {
                        val index = chunk.indexOfFirst { it == '\u00A0' }
                        chunk.delete(index, index + 1)
                    }

                    while (chunk.contains("\n\n\n")) {
                        val index = chunk.indexOf("\n\n\n")
                        chunk.delete(index, index + 1)
                    }

                    while (chunk.startsWith("\n\n")) {
                        chunk.delete(0, 1)
                    }

                    while (chunk.endsWith("\n\n")) {
                        chunk.delete(chunk.length - 2, chunk.length - 1)
                    }

                    if (chunkIndex != 0 && chunks[chunkIndex - 1] is ImageSpan && !chunk.startsWith("\n")) {
                        chunk.insert(0, "\n")
                    }

                    textView.text = chunk
                    textView.movementMethod = LinkMovementMethod.getInstance()

                    container.addView(textView)
                }

                is ImageSpan -> {
                    val imageView = AppCompatImageView(requireContext())

                    imageView.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )

                    imageView.scaleType = ImageView.ScaleType.FIT_XY

                    Picasso.get().load(chunk.source).into(imageView, object : Callback {
                        override fun onSuccess() {
                            val image = imageView.drawable
                            val targetHeight =
                                (imageView.width.toDouble() * (image.intrinsicHeight.toDouble() / image.intrinsicWidth.toDouble()))
                            imageView.layoutParams.height = targetHeight.toInt()
                        }

                        override fun onError(e: Exception) {
                            Timber.e(e)
                            imageView.isVisible = false
                        }
                    })

                    container.addView(imageView)
                }
            }
        }
    }

    private fun SpannableStringBuilder.applyStyle(textView: TextView) {
        val spans = getSpans(0, length - 1, Any::class.java)

        spans.forEach {
            when (it) {
                is BulletSpan -> {
                    val radius = resources.getDimension(R.dimen.bullet_radius).toInt()
                    val gap = resources.getDimension(R.dimen.bullet_gap).toInt()

                    val span = if (Build.VERSION.SDK_INT >= 28) {
                        BulletSpan(gap, textView.currentTextColor, radius)
                    } else {
                        BulletSpan(gap, textView.currentTextColor)
                    }

                    setSpan(
                        span,
                        getSpanStart(it),
                        getSpanEnd(it),
                        0
                    )

                    removeSpan(it)
                }

                is QuoteSpan -> {
                    val color = date.currentTextColor
                    val stripe = resources.getDimension(R.dimen.quote_stripe_width).toInt()
                    val gap = resources.getDimension(R.dimen.quote_gap).toInt()

                    val span = if (Build.VERSION.SDK_INT >= 28) {
                        QuoteSpan(color, stripe, gap)
                    } else {
                        QuoteSpan(color)
                    }

                    setSpan(
                        span,
                        getSpanStart(it),
                        getSpanEnd(it),
                        0
                    )

                    removeSpan(it)
                }
            }
        }
    }
}