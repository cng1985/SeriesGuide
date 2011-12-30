
package com.battlelancer.seriesguide.ui;

import com.battlelancer.seriesguide.Constants;
import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.provider.SeriesContract;
import com.battlelancer.seriesguide.provider.SeriesContract.Shows;
import com.battlelancer.seriesguide.util.AnalyticsUtils;
import com.battlelancer.seriesguide.util.DBUtils;
import com.battlelancer.seriesguide.util.EulaHelper;
import com.battlelancer.seriesguide.util.TaskManager;
import com.battlelancer.seriesguide.util.UpdateTask;
import com.battlelancer.seriesguide.util.Utils;
import com.battlelancer.thetvdbapi.ImageCache;
import com.battlelancer.thetvdbapi.TheTVDB;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;
import net.londatiga.android.QuickAction.OnActionItemClickListener;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.ActionBar;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ShowsActivity extends BaseActivity implements AbsListView.OnScrollListener,
        LoaderManager.LoaderCallbacks<Cursor>, ActionBar.OnNavigationListener {

    private boolean mBusy;

    private static final int UPDATE_SUCCESS = 100;

    private static final int UPDATE_INCOMPLETE = 104;

    private static final int CONTEXT_DELETE_ID = 200;

    private static final int CONTEXT_UPDATE_ID = 201;

    private static final int CONTEXT_MARKNEXT_ID = 203;

    private static final int CONTEXT_FAVORITE_ID = 204;

    private static final int CONTEXT_UNFAVORITE_ID = 205;

    private static final int CONFIRM_DELETE_DIALOG = 304;

    private static final int SORT_DIALOG = 306;

    private static final int BETA_WARNING_DIALOG = 307;

    private static final int LOADER_ID = 900;

    // Background Task States
    private static final String STATE_ART_IN_PROGRESS = "seriesguide.art.inprogress";

    private static final String STATE_ART_PATHS = "seriesguide.art.paths";

    private static final String STATE_ART_INDEX = "seriesguide.art.index";

    // Show Filter Ids
    private static final int SHOWFILTER_ALL = 0;

    private static final int SHOWFILTER_FAVORITES = 1;

    private static final int SHOWFILTER_UNSEENEPISODES = 2;

    private static final String FILTER_ID = "filterid";

    private static final int VER_TRAKT_SEC_CHANGES = 131;

    private Bundle mSavedState;

    private FetchPosterTask mArtTask;

    private SlowAdapter mAdapter;

    private Constants.ShowSorting mSorting;

    private long mToDeleteId;

    private boolean mIsPreventLoaderRestart;

    /**
     * Google Analytics helper method for easy event tracking.
     * 
     * @param label
     */
    public void fireTrackerEvent(String label) {
        AnalyticsUtils.getInstance(this).trackEvent("Shows", "Click", label, 0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.shows);

        if (!EulaHelper.hasAcceptedEula(this)) {
            EulaHelper.showEula(false, this);
        }

        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());

        // setup action bar filter list (! use different layouts for ABS)
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        ArrayAdapter<CharSequence> mActionBarList = ArrayAdapter.createFromResource(this,
                R.array.showfilter_list, R.layout.abs__simple_spinner_item);
        mActionBarList.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        actionBar.setListNavigationCallbacks(mActionBarList, this);

        // try to restore previously set show filter
        int showfilter = prefs.getInt(SeriesGuidePreferences.KEY_SHOWFILTER, 0);
        actionBar.setSelectedNavigationItem(showfilter);
        // prevent the onNavigationItemSelected listener from reacting
        mIsPreventLoaderRestart = true;

        updatePreferences(prefs);

        // setup show adapter
        String[] from = new String[] {
                SeriesContract.Shows.TITLE, SeriesContract.Shows.NEXTTEXT,
                SeriesContract.Shows.AIRSTIME, SeriesContract.Shows.NETWORK,
                SeriesContract.Shows.POSTER
        };
        int[] to = new int[] {
                R.id.seriesname, R.id.TextViewShowListNextEpisode, R.id.TextViewShowListAirtime,
                R.id.TextViewShowListNetwork, R.id.showposter
        };
        int layout = R.layout.show_rowairtime;

        mAdapter = new SlowAdapter(this, layout, null, from, to, 0);

        GridView list = (GridView) findViewById(R.id.showlist);
        list.setAdapter(mAdapter);
        list.setFastScrollEnabled(true);
        list.setOnItemClickListener(new OnItemClickListener() {

            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                Intent i = new Intent(ShowsActivity.this, OverviewActivity.class);
                i.putExtra(Shows._ID, String.valueOf(id));
                startActivity(i);
            }
        });
        list.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View v, int arg2, final long id) {
                final QuickAction quickAction = new QuickAction(ShowsActivity.this,
                        QuickAction.VERTICAL);

                // decide whether it is favorite or unfavorite
                final Cursor show = getContentResolver().query(
                        Shows.buildShowUri(String.valueOf(id)), new String[] {
                            Shows.FAVORITE
                        }, null, null, null);
                show.moveToFirst();
                if (show.getInt(0) == 0) {
                    quickAction.addActionItem(new ActionItem(CONTEXT_FAVORITE_ID,
                            getString(R.string.context_favorite)));
                } else {
                    quickAction.addActionItem(new ActionItem(CONTEXT_UNFAVORITE_ID,
                            getString(R.string.context_unfavorite)));
                }
                show.close();

                // add remaining actions
                quickAction.addActionItem(new ActionItem(CONTEXT_MARKNEXT_ID,
                        getString(R.string.context_marknext)));
                quickAction.addActionItem(new ActionItem(CONTEXT_UPDATE_ID,
                        getString(R.string.context_updateshow)));
                quickAction.addActionItem(new ActionItem(CONTEXT_DELETE_ID,
                        getString(R.string.delete_show)));

                // on click listeners
                quickAction.setOnActionItemClickListener(new OnActionItemClickListener() {
                    @Override
                    public void onItemClick(QuickAction source, int pos, int actionId) {
                        switch (actionId) {
                            case CONTEXT_FAVORITE_ID: {
                                fireTrackerEvent("Favorite show");

                                ContentValues values = new ContentValues();
                                values.put(Shows.FAVORITE, true);
                                getContentResolver().update(Shows.buildShowUri(String.valueOf(id)),
                                        values, null, null);
                                Toast.makeText(ShowsActivity.this, getString(R.string.favorited),
                                        Toast.LENGTH_SHORT).show();
                                break;
                            }
                            case CONTEXT_UNFAVORITE_ID: {
                                fireTrackerEvent("Unfavorite show");

                                ContentValues values = new ContentValues();
                                values.put(Shows.FAVORITE, false);
                                getContentResolver().update(Shows.buildShowUri(String.valueOf(id)),
                                        values, null, null);
                                Toast.makeText(ShowsActivity.this, getString(R.string.unfavorited),
                                        Toast.LENGTH_SHORT).show();
                                break;
                            }
                            case CONTEXT_DELETE_ID:
                                fireTrackerEvent("Delete show");

                                if (!TaskManager.getInstance(ShowsActivity.this)
                                        .isUpdateTaskRunning(true)) {
                                    mToDeleteId = id;
                                    showDialog(CONFIRM_DELETE_DIALOG);
                                }
                                break;
                            case CONTEXT_UPDATE_ID:
                                fireTrackerEvent("Update show");

                                performUpdateTask(false, String.valueOf(id));
                                break;
                            case CONTEXT_MARKNEXT_ID:
                                fireTrackerEvent("Mark next episode");

                                DBUtils.markNextEpisode(ShowsActivity.this, id);
                                Utils.updateLatestEpisode(ShowsActivity.this, String.valueOf(id));
                                break;
                        }
                    }
                });

                quickAction.show(v);
                return true;
            }
        });
        list.setOnScrollListener(this);
        View emptyView = findViewById(R.id.empty);
        if (emptyView != null) {
            list.setEmptyView(emptyView);
        }

        // start loading data
        Bundle args = new Bundle();
        args.putInt(FILTER_ID, showfilter);
        getSupportLoaderManager().initLoader(LOADER_ID, args, this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        AnalyticsUtils.getInstance(this).trackPageView("/Shows");

        // auto-update
        final SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        final boolean isAutoUpdateEnabled = prefs.getBoolean(SeriesGuidePreferences.KEY_AUTOUPDATE,
                true);
        if (isAutoUpdateEnabled && !TaskManager.getInstance(this).isUpdateTaskRunning(false)) {
            // allow auto-update if 12 hours have passed
            final long previousUpdateTime = prefs.getLong(SeriesGuidePreferences.KEY_LASTUPDATE, 0);
            long currentTime = System.currentTimeMillis();
            final boolean isTime = currentTime - (previousUpdateTime) > 12 * DateUtils.HOUR_IN_MILLIS;

            if (isTime) {
                // allow auto-update only on allowed connection
                final boolean isAutoUpdateWlanOnly = prefs.getBoolean(
                        SeriesGuidePreferences.KEY_AUTOUPDATEWLANONLY, true);
                boolean isOnAllowedConnection = true;
                if (isAutoUpdateWlanOnly) {
                    // abort if we are not on WiFi
                    if (!Utils.isWifiConnected(this)) {
                        isOnAllowedConnection = false;
                    }
                }

                if (isOnAllowedConnection) {
                    performUpdateTask(false, null);
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Utils.updateLatestEpisodes(this);
        if (mSavedState != null) {
            restoreLocalState(mSavedState);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        onCancelTasks();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        restoreLocalState(savedInstanceState);
        mSavedState = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        saveArtTask(outState);
        mSavedState = outState;
    }

    private void restoreLocalState(Bundle savedInstanceState) {
        restoreArtTask(savedInstanceState);
    }

    private void restoreArtTask(Bundle savedInstanceState) {
        if (savedInstanceState.getBoolean(STATE_ART_IN_PROGRESS)) {
            ArrayList<String> paths = savedInstanceState.getStringArrayList(STATE_ART_PATHS);
            int index = savedInstanceState.getInt(STATE_ART_INDEX);

            if (paths != null) {
                mArtTask = (FetchPosterTask) new FetchPosterTask(paths, index).execute();
                AnalyticsUtils.getInstance(this).trackEvent("Shows", "Task Lifecycle",
                        "Art Task Restored", 0);
            }
        }
    }

    private void saveArtTask(Bundle outState) {
        final FetchPosterTask task = mArtTask;
        if (task != null && task.getStatus() != AsyncTask.Status.FINISHED) {
            task.cancel(true);

            outState.putBoolean(STATE_ART_IN_PROGRESS, true);
            outState.putStringArrayList(STATE_ART_PATHS, task.mPaths);
            outState.putInt(STATE_ART_INDEX, task.mFetchCount.get());

            mArtTask = null;

            AnalyticsUtils.getInstance(this).trackEvent("Shows", "Task Lifecycle",
                    "Art Task Saved", 0);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case CONFIRM_DELETE_DIALOG:
                return new AlertDialog.Builder(this).setMessage(getString(R.string.confirm_delete))
                        .setPositiveButton(getString(R.string.delete_show), new OnClickListener() {

                            public void onClick(DialogInterface dialog, int which) {

                                final ProgressDialog progress = new ProgressDialog(
                                        ShowsActivity.this);
                                progress.setCancelable(false);
                                progress.show();

                                new Thread(new Runnable() {
                                    public void run() {
                                        DBUtils.deleteShow(getApplicationContext(),
                                                String.valueOf(mToDeleteId));
                                        if (progress.isShowing()) {
                                            progress.dismiss();
                                        }
                                    }
                                }).start();
                            }
                        }).setNegativeButton(getString(R.string.dontdelete_show), null).create();
            case BETA_WARNING_DIALOG:
                /* Used for unstable beta releases */
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.app_name)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(getString(R.string.betawarning))
                        .setPositiveButton(R.string.gobreak, null)
                        .setNeutralButton(getString(R.string.download_stable),
                                new OnClickListener() {

                                    public void onClick(DialogInterface dialog, int which) {
                                        try {
                                            Intent myIntent = new Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("market://details?id=com.battlelancer.seriesguide"));
                                            startActivity(myIntent);
                                        } catch (ActivityNotFoundException e) {
                                            Intent myIntent = new Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse("http://market.android.com/details?id=com.battlelancer.seriesguide"));
                                            startActivity(myIntent);
                                        }
                                        finish();
                                    }
                                }).create();
            case SORT_DIALOG:
                final CharSequence[] items = getResources().getStringArray(R.array.shsorting);

                return new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.pref_showsorting))
                        .setSingleChoiceItems(items, mSorting.index(),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int item) {
                                        SharedPreferences.Editor prefEditor = PreferenceManager
                                                .getDefaultSharedPreferences(
                                                        getApplicationContext()).edit();
                                        prefEditor
                                                .putString(
                                                        SeriesGuidePreferences.KEY_SHOWSSORTORDER,
                                                        (getResources()
                                                                .getStringArray(R.array.shsortingData))[item]);
                                        prefEditor.commit();
                                        removeDialog(SORT_DIALOG);
                                    }
                                }).create();
        }
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.seriesguide_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            final CharSequence[] items = getResources().getStringArray(R.array.shsorting);
            menu.findItem(R.id.menu_showsortby).setTitle(
                    getString(R.string.sort) + ": " + items[mSorting.index()]);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.menu_search:
                fireTrackerEvent("Search");

                onSearchRequested();
                return true;
            case R.id.menu_update:
                fireTrackerEvent("Update");

                performUpdateTask(false, null);
                return true;
            case R.id.menu_upcoming:
                startActivity(new Intent(this, UpcomingRecentActivity.class));
                return true;
            case R.id.menu_new_show:
                startActivity(new Intent(this, AddActivity.class));
                return true;
            case R.id.menu_showsortby:
                fireTrackerEvent("Sort shows");

                showDialog(SORT_DIALOG);
                return true;
            case R.id.menu_updateart:
                fireTrackerEvent("Fetch missing posters");

                if (isArtTaskRunning()) {
                    return true;
                }

                // already fail if there is no external storage
                if (!Utils.isExtStorageAvailable()) {
                    Toast.makeText(this, getString(R.string.update_nosdcard), Toast.LENGTH_LONG)
                            .show();
                } else {
                    Toast.makeText(this, getString(R.string.update_inbackground), Toast.LENGTH_LONG)
                            .show();
                    mArtTask = (FetchPosterTask) new FetchPosterTask().execute();
                }
                return true;
            case R.id.menu_preferences:
                startActivity(new Intent(this, SeriesGuidePreferences.class));

                return true;
            case R.id.menu_fullupdate:
                fireTrackerEvent("Full Update");

                performUpdateTask(true, null);
                return true;
            case R.id.menu_feedback: {
                fireTrackerEvent("Feedback");

                final Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                intent.setType("plain/text");
                intent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[] {
                    SeriesGuidePreferences.SUPPORT_MAIL
                });
                intent.putExtra(android.content.Intent.EXTRA_SUBJECT,
                        "SeriesGuide " + Utils.getVersion(this) + " Feedback");
                intent.putExtra(android.content.Intent.EXTRA_TEXT, "");

                startActivity(Intent.createChooser(intent, "Send mail..."));

                return true;
            }
            case R.id.menu_help: {
                fireTrackerEvent("Help");

                Intent myIntent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(SeriesGuidePreferences.HELP_URL));

                startActivity(myIntent);

                return true;
            }
            default: {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    private void performUpdateTask(boolean isFullUpdate, String showId) {
        int messageId;
        UpdateTask task;
        if (isFullUpdate) {
            messageId = R.string.update_full;
            task = (UpdateTask) new UpdateTask(true, this);
        } else {
            if (showId == null) {
                // (delta) update all shows
                messageId = R.string.update_delta;
                task = (UpdateTask) new UpdateTask(false, this);
            } else {
                // update a single show
                messageId = R.string.update_single;
                task = (UpdateTask) new UpdateTask(new String[] {
                    showId
                }, 0, "", this);
            }
        }
        TaskManager.getInstance(this).tryUpdateTask(task, messageId);
    }

    private class FetchPosterTask extends AsyncTask<Void, Void, Integer> {
        final AtomicInteger mFetchCount = new AtomicInteger();

        ArrayList<String> mPaths;

        private View mProgressOverlay;

        protected FetchPosterTask() {
        }

        protected FetchPosterTask(ArrayList<String> paths, int index) {
            mPaths = paths;
            mFetchCount.set(index);
        }

        @Override
        protected void onPreExecute() {
            // see if we already inflated the progress overlay
            mProgressOverlay = findViewById(R.id.overlay_update);
            if (mProgressOverlay == null) {
                mProgressOverlay = ((ViewStub) findViewById(R.id.stub_update)).inflate();
            }
            showOverlay(mProgressOverlay);
            // setup the progress overlay
            TextView mUpdateStatus = (TextView) mProgressOverlay
                    .findViewById(R.id.textViewUpdateStatus);
            mUpdateStatus.setText("");

            ProgressBar updateProgress = (ProgressBar) mProgressOverlay
                    .findViewById(R.id.ProgressBarShowListDet);
            updateProgress.setIndeterminate(true);

            View cancelButton = mProgressOverlay.findViewById(R.id.overlayCancel);
            cancelButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    onCancelTasks();
                }
            });
        }

        @Override
        protected Integer doInBackground(Void... params) {
            // fetch all available poster paths
            if (mPaths == null) {
                Cursor shows = getContentResolver().query(Shows.CONTENT_URI, new String[] {
                    Shows.POSTER
                }, null, null, null);

                // finish fast if there is no image to download
                if (shows.getCount() == 0) {
                    shows.close();
                    return UPDATE_SUCCESS;
                }

                mPaths = new ArrayList<String>();
                while (shows.moveToNext()) {
                    String imagePath = shows.getString(shows.getColumnIndexOrThrow(Shows.POSTER));
                    if (imagePath.length() != 0) {
                        mPaths.add(imagePath);
                    }
                }
                shows.close();
            }

            int resultCode = UPDATE_SUCCESS;
            final List<String> list = mPaths;
            final int count = list.size();
            final AtomicInteger fetchCount = mFetchCount;

            // try to fetch image for each path
            for (int i = fetchCount.get(); i < count; i++) {
                if (isCancelled()) {
                    // code doesn't matter as onPostExecute will not be called
                    return UPDATE_INCOMPLETE;
                }

                if (!TheTVDB.fetchArt(list.get(i), true, ShowsActivity.this)) {
                    resultCode = UPDATE_INCOMPLETE;
                }

                fetchCount.incrementAndGet();
            }

            getContentResolver().notifyChange(Shows.CONTENT_URI, null);

            return resultCode;
        }

        @Override
        protected void onPostExecute(Integer resultCode) {
            switch (resultCode) {
                case UPDATE_SUCCESS:
                    AnalyticsUtils.getInstance(ShowsActivity.this).trackEvent("Shows",
                            "Fetch missing posters", "Success", 0);

                    Toast.makeText(getApplicationContext(), getString(R.string.update_success),
                            Toast.LENGTH_SHORT).show();
                    break;
                case UPDATE_INCOMPLETE:
                    AnalyticsUtils.getInstance(ShowsActivity.this).trackEvent("Shows",
                            "Fetch missing posters", "Incomplete", 0);

                    Toast.makeText(getApplicationContext(),
                            getString(R.string.imagedownload_incomplete), Toast.LENGTH_LONG).show();
                    break;
            }

            hideOverlay(mProgressOverlay);
        }

        @Override
        protected void onCancelled() {
            hideOverlay(mProgressOverlay);
        }
    }

    private boolean isArtTaskRunning() {
        if (mArtTask != null && mArtTask.getStatus() == AsyncTask.Status.RUNNING) {
            Toast.makeText(this, getString(R.string.update_inprogress), Toast.LENGTH_LONG).show();
            return true;
        } else {
            return false;
        }
    }

    public void onCancelTasks() {
        if (mArtTask != null && mArtTask.getStatus() == AsyncTask.Status.RUNNING) {
            mArtTask.cancel(true);
            mArtTask = null;

            AnalyticsUtils.getInstance(this).trackEvent("Shows", "Task Lifecycle",
                    "Art Task Canceled", 0);
        }
    }

    public void showOverlay(View overlay) {
        overlay.startAnimation(AnimationUtils
                .loadAnimation(getApplicationContext(), R.anim.fade_in));
        overlay.setVisibility(View.VISIBLE);
    }

    public void hideOverlay(View overlay) {
        overlay.startAnimation(AnimationUtils.loadAnimation(getApplicationContext(),
                R.anim.fade_out));
        overlay.setVisibility(View.GONE);
    }

    private void requery() {
        int filterId = getSupportActionBar().getSelectedNavigationIndex();
        // just reuse the onNavigationItemSelected callback method
        onNavigationItemSelected(filterId, filterId);
    }

    final OnSharedPreferenceChangeListener mPrefsListener = new OnSharedPreferenceChangeListener() {

        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            boolean isAffectingChange = false;
            if (key.equalsIgnoreCase(SeriesGuidePreferences.KEY_SHOWSSORTORDER)) {
                updateSorting(sharedPreferences);
                isAffectingChange = true;
            }
            // TODO: maybe don't requery every time a pref changes (possibly
            // problematic if you change a setting in the settings activity)
            if (isAffectingChange) {
                requery();
            }
        }
    };

    /**
     * Called once on activity creation to load initial settings and display
     * one-time information dialogs.
     */
    private void updatePreferences(SharedPreferences prefs) {
        updateSorting(prefs);

        // between-version upgrade code
        int lastVersion = prefs.getInt(SeriesGuidePreferences.KEY_VERSION, -1);
        try {
            int currentVersion = getPackageManager().getPackageInfo(getPackageName(),
                    PackageManager.GET_META_DATA).versionCode;
            if (currentVersion > lastVersion) {
                if (lastVersion < VER_TRAKT_SEC_CHANGES) {
                    prefs.edit().putString(SeriesGuidePreferences.KEY_TRAKTPWD, null).commit();
                    prefs.edit().putString(SeriesGuidePreferences.KEY_SECURE, null).commit();
                }

                // // BETA warning dialog switch
                // showDialog(BETA_WARNING_DIALOG);
                // showDialog(WHATS_NEW_DIALOG);

                // set this as lastVersion
                prefs.edit().putInt(SeriesGuidePreferences.KEY_VERSION, currentVersion).commit();
            }
        } catch (NameNotFoundException e) {
            // this should never happen
        }

        prefs.registerOnSharedPreferenceChangeListener(mPrefsListener);
    }

    /**
     * Fetch the sorting preference and store it in this class.
     * 
     * @param prefs
     * @return Returns true if the value changed, false otherwise.
     */
    private boolean updateSorting(SharedPreferences prefs) {
        final Constants.ShowSorting oldSorting = mSorting;
        final CharSequence[] items = getResources().getStringArray(R.array.shsortingData);
        final String sortsetting = prefs.getString(SeriesGuidePreferences.KEY_SHOWSSORTORDER,
                "alphabetic");

        for (int i = 0; i < items.length; i++) {
            if (sortsetting.equals(items[i])) {
                mSorting = Constants.ShowSorting.values()[i];
                break;
            }
        }

        AnalyticsUtils.getInstance(ShowsActivity.this).trackEvent("Shows", "Sorting",
                mSorting.name(), 0);

        return oldSorting != mSorting;
    }

    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String selection = null;
        String[] selectionArgs = null;

        int filterId = args.getInt(FILTER_ID);
        switch (filterId) {
            case SHOWFILTER_ALL:
                // do nothing, leave selection null
                break;
            case SHOWFILTER_FAVORITES:
                selection = Shows.FAVORITE + "=?";
                selectionArgs = new String[] {
                    "1"
                };
                break;
            case SHOWFILTER_UNSEENEPISODES:
                selection = Shows.NEXTAIRDATE + "!=? AND julianday(" + Shows.NEXTAIRDATE
                        + ") <= julianday('now')";
                selectionArgs = new String[] {
                    DBUtils.UNKNOWN_NEXT_AIR_DATE
                };
                break;
        }

        return new CursorLoader(this, Shows.CONTENT_URI, ShowsQuery.PROJECTION, selection,
                selectionArgs, mSorting.query());
    }

    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Swap the new cursor in. (The framework will take care of closing the
        // old cursor once we return.)
        mAdapter.swapCursor(data);
    }

    public void onLoaderReset(Loader<Cursor> arg0) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed. We need to make sure we are no
        // longer using it.
        mAdapter.swapCursor(null);
    }

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        // only handle events after the event caused when creating the activity
        if (mIsPreventLoaderRestart) {
            mIsPreventLoaderRestart = false;
        } else {
            // requery with the new filter
            Bundle args = new Bundle();
            args.putInt(FILTER_ID, itemPosition);
            getSupportLoaderManager().restartLoader(LOADER_ID, args, this);

            // save the selected filter back to settings
            Editor editor = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                    .edit();
            editor.putInt(SeriesGuidePreferences.KEY_SHOWFILTER, itemPosition);
            editor.commit();
        }
        return true;
    }

    private interface ShowsQuery {
        String[] PROJECTION = {
                BaseColumns._ID, Shows.TITLE, Shows.NEXTTEXT, Shows.AIRSTIME, Shows.NETWORK,
                Shows.POSTER, Shows.AIRSDAYOFWEEK, Shows.STATUS, Shows.NEXTAIRDATETEXT
        };

        // int _ID = 0;

        int TITLE = 1;

        int NEXTTEXT = 2;

        int AIRSTIME = 3;

        int NETWORK = 4;

        int POSTER = 5;

        int AIRSDAYOFWEEK = 6;

        int STATUS = 7;

        int NEXTAIRDATETEXT = 8;
    }

    private class SlowAdapter extends SimpleCursorAdapter {

        private LayoutInflater mLayoutInflater;

        private int mLayout;

        public SlowAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags) {
            super(context, layout, c, from, to, flags);

            mLayoutInflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mLayout = layout;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (!mDataValid) {
                throw new IllegalStateException(
                        "this should only be called when the cursor is valid");
            }
            if (!mCursor.moveToPosition(position)) {
                throw new IllegalStateException("couldn't move cursor to position " + position);
            }

            ViewHolder viewHolder;

            if (convertView == null) {
                convertView = mLayoutInflater.inflate(mLayout, null);

                viewHolder = new ViewHolder();
                viewHolder.name = (TextView) convertView.findViewById(R.id.seriesname);
                viewHolder.network = (TextView) convertView
                        .findViewById(R.id.TextViewShowListNetwork);
                viewHolder.next = (TextView) convertView.findViewById(R.id.next);
                viewHolder.episode = (TextView) convertView
                        .findViewById(R.id.TextViewShowListNextEpisode);
                viewHolder.episodeTime = (TextView) convertView.findViewById(R.id.episodetime);
                viewHolder.airsTime = (TextView) convertView
                        .findViewById(R.id.TextViewShowListAirtime);
                viewHolder.poster = (ImageView) convertView.findViewById(R.id.showposter);

                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            // set text properties immediately
            viewHolder.name.setText(mCursor.getString(ShowsQuery.TITLE));
            viewHolder.network.setText(mCursor.getString(ShowsQuery.NETWORK));

            // next episode info
            String fieldValue = mCursor.getString(ShowsQuery.NEXTTEXT);
            if (fieldValue.length() == 0) {
                // show show status if there are currently no more
                // episodes
                int status = mCursor.getInt(ShowsQuery.STATUS);

                // Continuing == 1 and Ended == 0
                if (status == 1) {
                    viewHolder.next.setText(getString(R.string.show_isalive));
                } else if (status == 0) {
                    viewHolder.next.setText(getString(R.string.show_isnotalive));
                } else {
                    viewHolder.next.setText("");
                }
                viewHolder.episode.setText("");
                viewHolder.episodeTime.setText("");
            } else {
                viewHolder.next.setText(getString(R.string.nextepisode));
                viewHolder.episode.setText(fieldValue);
                fieldValue = mCursor.getString(ShowsQuery.NEXTAIRDATETEXT);
                viewHolder.episodeTime.setText(fieldValue);
            }

            // airday
            String[] values = Utils.parseMillisecondsToTime(mCursor.getLong(ShowsQuery.AIRSTIME),
                    mCursor.getString(ShowsQuery.AIRSDAYOFWEEK), ShowsActivity.this);
            viewHolder.airsTime.setText(values[1] + " " + values[0]);

            // set poster only when not busy scrolling
            final String path = mCursor.getString(ShowsQuery.POSTER);
            if (!mBusy) {
                // load poster
                setPosterBitmap(viewHolder.poster, path, false);

                // Null tag means the view has the correct data
                viewHolder.poster.setTag(null);
            } else {
                // only load in-memory poster
                setPosterBitmap(viewHolder.poster, path, true);
            }

            return convertView;
        }
    }

    public final class ViewHolder {

        public TextView name;

        public TextView network;

        public TextView next;

        public TextView episode;

        public TextView episodeTime;

        public TextView airsTime;

        public ImageView poster;
    }

    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
    }

    public void onScrollStateChanged(AbsListView view, int scrollState) {
        switch (scrollState) {
            case OnScrollListener.SCROLL_STATE_IDLE:
                mBusy = false;

                int count = view.getChildCount();
                for (int i = 0; i < count; i++) {
                    final ViewHolder holder = (ViewHolder) view.getChildAt(i).getTag();
                    final ImageView poster = holder.poster;
                    if (poster.getTag() != null) {
                        setPosterBitmap(poster, (String) poster.getTag(), false);
                        poster.setTag(null);
                    }
                }

                break;
            case OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
                mBusy = false;
                break;
            case OnScrollListener.SCROLL_STATE_FLING:
                mBusy = true;
                break;
        }
    }

    /**
     * If {@code isBusy} is {@code true}, then the image is only loaded if it is
     * in memory. In every other case a place-holder is shown.
     * 
     * @param poster
     * @param path
     * @param isBusy
     */
    private void setPosterBitmap(ImageView poster, String path, boolean isBusy) {
        Bitmap bitmap = null;
        if (path.length() != 0) {
            bitmap = ImageCache.getInstance(this).getThumb(path, isBusy);
        }

        if (bitmap != null) {
            poster.setImageBitmap(bitmap);
            poster.setTag(null);
        } else {
            // set placeholder
            poster.setImageResource(R.drawable.show_generic);
            // Non-null tag means the view still needs to load it's data
            poster.setTag(path);
        }
    }
}
