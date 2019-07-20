package kz.production.mukhit.hackernews.ui.stories.top.view


import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_top.*
import kz.production.mukhit.hackernews.R
import kz.production.mukhit.hackernews.data.model.Story
import kz.production.mukhit.hackernews.data.remote.AppDatabase
import kz.production.mukhit.hackernews.data.remote.DatabaseClient
import kz.production.mukhit.hackernews.data.remote.entity.StoryEntity
import kz.production.mukhit.hackernews.ui.base.view.BaseFragment
import kz.production.mukhit.hackernews.ui.comment.view.CommentActivity
import kz.production.mukhit.hackernews.ui.stories.top.interactor.TopMvpInteractor
import kz.production.mukhit.hackernews.ui.stories.top.presenter.TopMvpPresenter
import kz.production.mukhit.hackernews.ui.webview.WebViewActivity
import kz.production.mukhit.hackernews.utils.EndlessScrollListener
import javax.inject.Inject

class TopFragment : BaseFragment(), TopView, StoryItemClickListener {


    @Inject
    lateinit var presenter: TopMvpPresenter<TopView, TopMvpInteractor>

    @Inject
    internal lateinit var mAdapter: StoryAdapter

    @Inject
    lateinit var mLayoutManager: LinearLayoutManager

    private var PAGE = 0
    private var LIMIT = 10

    val currentStoryList : MutableLiveData<List<Long>> = MutableLiveData()
    val loadingState : MutableLiveData<STATE> = MutableLiveData()
    lateinit var mDatabase : AppDatabase


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_top, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        presenter.onAttach(this)
        super.onViewCreated(view, savedInstanceState)
    }


    override fun setUp() {
        mDatabase = DatabaseClient.getInstance(getBaseActivity()!!).appDatabase

        topRecylerView.layoutManager = mLayoutManager
        topRecylerView.adapter=mAdapter
        mAdapter.setList(ArrayList())
        mAdapter.addStoryItemClcikListener(this)

        setupEndlessScrolListener()

        loadingState.observe(this, Observer{
            if(it == STATE.NOT_LOADING){
                presenter.cancelAllLoading()
            }
        })

        mAdapter.setList(ArrayList())
        if(isNetworkAvailable()) {
            loadingState.value = STATE.LOADING
            presenter.onLoadingTopStoryList()
        }
        else{
            PAGE=0
            loadingState.value = STATE.FROM_ROOM
            getAllStoryInDb(LIMIT,PAGE*LIMIT)
        }

    }

    override fun setupEndlessScrolListener() {
        val scrollListener = object : EndlessScrollListener(mLayoutManager){

            override fun onLoadMore(page: Int, totalItemsCount: Int, view: RecyclerView?) {

                if(loadingState.value == STATE.NOT_LOADING) {
                    PAGE++
                    val start = PAGE * LIMIT
                    val end = (PAGE+1) * LIMIT
                    val maximumPage = currentStoryList.value?.size!! / LIMIT
                    loadingState.value = STATE.LOADING
                    if(PAGE==maximumPage){

                        val l = currentStoryList.value?.subList(start, currentStoryList.value?.size!!)
                        presenter.onLoadStoryItem(l)
                    }
                    else{
                        val l = currentStoryList.value?.subList(start,end)
                        presenter.onLoadStoryItem(l)
                    }
                }
                else if(loadingState.value == STATE.FROM_ROOM){
                    PAGE++
                    loadingState.value = STATE.LOADING
                    getAllStoryInDb(LIMIT,PAGE*LIMIT)
                }

            }
        }
        topRecylerView.addOnScrollListener(scrollListener as EndlessScrollListener)
    }

    override fun onDestroyView() {
        presenter.onDetach()
        super.onDestroyView()
    }

    override fun setList(list: List<Long>?) {
        currentStoryList.value = list
        presenter.onLoadStoryItem(list?.subList(PAGE*LIMIT,(PAGE+1) * LIMIT))
    }

    override fun getAllStoryInDb(limit: Int, offset: Int) {
        val allStories = mDatabase.storyDao().getTopStories(limit,offset,true)

        for(item in allStories){
            val story = Story()

            story.id = item.id
            story.author = item.author
            story.commentCount = item.commentCount
            story.score = item.score
            story.time = item.time
            story.title = item.title
            story.url = item.url
            story.type = item.type

            mAdapter.setStoryItem(story)

        }
        loadingState.value = STATE.FROM_ROOM
    }

    override fun setStoryList(storyList: List<Story>?) {

        topRecylerView.post(Runnable {
            for(item in storyList!!){
                insertToDb(item)
                mAdapter.setStoryItem(item)
            }
        })

        loadingState.value = STATE.NOT_LOADING
    }

    override fun insertToDb(story : Story) {
        val storyListInDb = mDatabase.storyDao().getStoryById(story.id!!)
        if(storyListInDb.size > 0){
            storyListInDb[0].isTop = true
            mDatabase.storyDao().update(storyListInDb[0])
        }
        else{
            val storyEntity = StoryEntity()
            storyEntity.id = story.id
            storyEntity.author = story.author
            storyEntity.commentCount = story.commentCount
            storyEntity.title = story.title
            storyEntity.score = story.score
            storyEntity.time = story.time
            storyEntity.url = story.url
            storyEntity.type = story.type
            storyEntity.isTop = true

            mDatabase.storyDao().insert(storyEntity)
        }
    }

    override fun onCommentClick(story : Story) {
        val intent = Intent(activity?.baseContext,CommentActivity::class.java)
        intent.putExtra("story",story)
        loadingState.value = STATE.NOT_LOADING
        startActivity(intent)
    }

    override fun onItemClick(webUrl: String) {
        val intent = Intent(activity?.baseContext,WebViewActivity::class.java)
        intent.putExtra("base_url",webUrl)
        loadingState.value = STATE.NOT_LOADING
        startActivity(intent)
    }

    enum class STATE{
        LOADING,
        NOT_LOADING,
        FROM_ROOM
    }
}