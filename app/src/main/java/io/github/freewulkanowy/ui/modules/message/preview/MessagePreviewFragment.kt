package io.github.freewulkanowy.ui.modules.message.preview

import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import io.github.freewulkanowy.R
import io.github.freewulkanowy.data.db.entities.Message
import io.github.freewulkanowy.data.db.entities.MessageWithAttachment
import io.github.freewulkanowy.databinding.FragmentMessagePreviewBinding
import io.github.freewulkanowy.ui.base.BaseFragment
import io.github.freewulkanowy.ui.modules.main.MainActivity
import io.github.freewulkanowy.ui.modules.main.MainView
import io.github.freewulkanowy.ui.modules.message.send.SendMessageActivity
import io.github.freewulkanowy.utils.serializable
import io.github.freewulkanowy.utils.shareText
import javax.inject.Inject

@AndroidEntryPoint
class MessagePreviewFragment :
    BaseFragment<FragmentMessagePreviewBinding>(R.layout.fragment_message_preview),
    MessagePreviewView, MainView.TitledView {

    @Inject
    lateinit var presenter: MessagePreviewPresenter

    @Inject
    lateinit var previewAdapter: MessagePreviewAdapter

    private var menuReplyButton: MenuItem? = null

    private var menuForwardButton: MenuItem? = null

    private var menuRestoreButton: MenuItem? = null

    private var menuDeleteButton: MenuItem? = null

    private var menuDeleteForeverButton: MenuItem? = null

    private var menuShareButton: MenuItem? = null

    private var menuMarkReadButton: MenuItem? = null

    private var menuPrintButton: MenuItem? = null

    private var menuMuteButton: MenuItem? = null

    override val titleStringId: Int
        get() = R.string.message_title

    override val deleteMessageSuccessString: String
        get() = getString(R.string.message_delete_success)

    override val muteMessageSuccessString: String
        get() = getString(R.string.message_mute_success)

    override val unmuteMessageSuccessString: String
        get() = getString(R.string.message_unmute_success)

    override val restoreMessageSuccessString: String
        get() = getString(R.string.message_restore_success)

    override val messageNoSubjectString: String
        get() = getString(R.string.message_no_subject)

    override val printHTML: String
        get() = requireContext().assets.open("message-print-page.html").bufferedReader()
            .use { it.readText() }

    override val messageNotExists: String
        get() = getString(R.string.message_not_exists)

    companion object {
        private const val MESSAGE_ARG_KEY = "message"

        fun newInstance(message: Message) = MessagePreviewFragment().apply {
            arguments = bundleOf(MESSAGE_ARG_KEY to message)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentMessagePreviewBinding.bind(view)
        messageContainer = binding.messagePreviewContainer
        presenter.onAttachView(
            view = this,
            message = requireArguments().serializable(MESSAGE_ARG_KEY),
        )
    }

    override fun initView() {
        binding.messagePreviewErrorDetails.setOnClickListener { presenter.onDetailsClick() }

        with(binding.messagePreviewRecycler) {
            layoutManager = LinearLayoutManager(context)
            adapter = previewAdapter
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.action_menu_message_preview, menu)
        menuReplyButton = menu.findItem(R.id.messagePreviewMenuReply)
        menuForwardButton = menu.findItem(R.id.messagePreviewMenuForward)
        menuRestoreButton = menu.findItem(R.id.messagePreviewMenuRestore)
        menuDeleteButton = menu.findItem(R.id.messagePreviewMenuDelete)
        menuDeleteForeverButton = menu.findItem(R.id.messagePreviewMenuDeleteForever)
        menuShareButton = menu.findItem(R.id.messagePreviewMenuShare)
        menuMarkReadButton = menu.findItem(R.id.messagePreviewMenuMarkReadIncognito)
        menuPrintButton = menu.findItem(R.id.messagePreviewMenuPrint)
        menuMuteButton = menu.findItem(R.id.messagePreviewMenuMute)
        presenter.onCreateOptionsMenu()

        menu.findItem(R.id.mainMenuAccount).isVisible = false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.messagePreviewMenuReply -> presenter.onReply()
            R.id.messagePreviewMenuForward -> presenter.onForward()
            R.id.messagePreviewMenuRestore -> presenter.onMessageRestore()
            R.id.messagePreviewMenuDelete -> presenter.onMessageDelete()
            R.id.messagePreviewMenuDeleteForever -> presenter.onMessageDelete()
            R.id.messagePreviewMenuShare -> presenter.onShare()
            R.id.messagePreviewMenuMarkReadIncognito -> presenter.onMarkRead(item)
            R.id.messagePreviewMenuPrint -> presenter.onPrint()
            R.id.messagePreviewMenuMute -> presenter.onMute()
            else -> false
        }
    }

    override fun setMessageWithAttachment(item: MessageWithAttachment) {
        with(previewAdapter) {
            messageWithAttachment = item
            notifyDataSetChanged()
        }
    }

    override fun updateMuteToggleButton(isMuted: Boolean) {
        menuMuteButton?.setTitle(if (isMuted) R.string.message_unmute else R.string.message_mute)

    }

    override fun showProgress(show: Boolean) {
        binding.messagePreviewProgress.visibility = if (show) VISIBLE else GONE
    }

    override fun showContent(show: Boolean) {
        binding.messagePreviewRecycler.visibility = if (show) VISIBLE else GONE
    }

    override fun showOptions(show: Boolean, isReplayable: Boolean, isRestorable: Boolean, isUnreadIncognito: Boolean) {
        menuReplyButton?.isVisible = show && isReplayable
        menuForwardButton?.isVisible = show
        menuRestoreButton?.isVisible = show && isRestorable
        menuDeleteButton?.isVisible = show && !isRestorable
        menuDeleteForeverButton?.isVisible = show && isRestorable
        menuShareButton?.isVisible = show
        menuMarkReadButton?.isVisible = show && isUnreadIncognito
        menuPrintButton?.isVisible = show
        menuMuteButton?.isVisible = show && isReplayable
    }

    override fun showErrorView(show: Boolean) {
        binding.messagePreviewError.visibility = if (show) VISIBLE else GONE
    }

    override fun setErrorDetails(message: String) {
        binding.messagePreviewErrorMessage.text = message
    }

    override fun setErrorRetryCallback(callback: () -> Unit) {
        binding.messagePreviewErrorRetry.setOnClickListener { callback() }
    }

    override fun showMessage(@StringRes messageId: Int) {
        showMessage(getString(messageId))
    }

    override fun openMessageReply(message: Message?) {
        context?.let { it.startActivity(SendMessageActivity.getStartIntent(it, message, true)) }
    }

    override fun openMessageForward(message: Message?) {
        context?.let { it.startActivity(SendMessageActivity.getStartIntent(it, message)) }
    }

    override fun shareText(text: String, subject: String) {
        context?.shareText(text, subject)
    }

    override fun printDocument(html: String, jobName: String) {
        val webView = WebView(requireContext())
        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                false

            override fun onPageFinished(view: WebView, url: String) {
                createWebPrintJob(view, jobName)
            }
        }

        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/HTML", "UTF-8", null)
    }

    private fun createWebPrintJob(webView: WebView, jobName: String) {
        activity?.getSystemService<PrintManager>()?.let { printManager ->
            val printAdapter = webView.createPrintDocumentAdapter(jobName)

            printManager.print(
                jobName,
                printAdapter,
                PrintAttributes.Builder().build()
            )
        }
    }

    override fun popView() {
        (activity as MainActivity).popView()
    }

    override fun onDestroyView() {
        presenter.onDetachView()
        super.onDestroyView()
    }
}
