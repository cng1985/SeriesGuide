
package com.battlelancer.seriesguide.ui;

import com.battlelancer.seriesguide.R;
import com.battlelancer.seriesguide.provider.SeriesContract.Episodes;
import com.battlelancer.seriesguide.util.ImageDownloader;
import com.battlelancer.seriesguide.util.ShareUtils;
import com.battlelancer.seriesguide.util.Utils;
import com.battlelancer.thetvdbapi.SearchResult;
import com.jakewharton.apibuilder.ApiException;
import com.jakewharton.trakt.ServiceManager;
import com.jakewharton.trakt.TraktException;
import com.jakewharton.trakt.entities.ActivityItem;
import com.jakewharton.trakt.entities.ActivityItemBase;
import com.jakewharton.trakt.entities.TvShow;
import com.jakewharton.trakt.entities.TvShowEpisode;
import com.jakewharton.trakt.entities.UserProfile;
import com.jakewharton.trakt.enumerations.ActivityType;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class TraktFriendsFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<List<UserProfile>> {

    private TraktFriendsAdapter mAdapter;

    private boolean mDualPane;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Check to see if we have a frame in which to embed the details
        // fragment directly in the containing UI.
        View detailsFragment = getActivity().findViewById(R.id.fragment_details);
        mDualPane = detailsFragment != null && detailsFragment.getVisibility() == View.VISIBLE;

        setEmptyText(getString(R.string.friends_empty));

        mAdapter = new TraktFriendsAdapter(getActivity());
        setListAdapter(mAdapter);

        setListShown(false);

        getLoaderManager().initLoader(0, null, this);

    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        UserProfile friend = (UserProfile) getListView().getItemAtPosition(position);

        TvShow show = null;
        TvShowEpisode episode = null;

        if (friend.watching != null) {
            show = friend.watching.show;
            episode = friend.watching.episode;
        } else if (!friend.watched.isEmpty()) {
            ActivityItem activity = friend.watched.get(0);
            show = activity.show;
            episode = activity.episode;
        }

        if (episode != null && show != null) {
            Cursor episodeidquery = getActivity().getContentResolver().query(
                    Episodes.buildEpisodesOfShowUri(show.tvdbId), new String[] {
                        Episodes._ID
                    }, Episodes.NUMBER + "=? AND " + Episodes.SEASON + "=?", new String[] {
                            String.valueOf(episode.number), String.valueOf(episode.season)
                    }, null);

            if (episodeidquery.getCount() != 0) {
                // display the episode details if we have a match
                episodeidquery.moveToFirst();

                String episodeId;
                episodeId = episodeidquery.getString(0);

                showDetails(episodeId);
            } else {
                // offer to add the show if it's not in the show database yet
                SearchResult newshow = new SearchResult();
                newshow.tvdbid = show.tvdbId;
                newshow.title = show.title;
                newshow.overview = show.overview;
                AddDialogFragment.showAddDialog(newshow, getFragmentManager());
            }

            episodeidquery.close();
        }
    }

    private void showDetails(String episodeId) {
        if (mDualPane) {
            // Check if fragment is shown, create new if needed.
            EpisodeDetailsFragment detailsFragment = (EpisodeDetailsFragment) getFragmentManager()
                    .findFragmentById(R.id.fragment_details);
            if (detailsFragment == null
                    || !detailsFragment.getEpisodeId().equalsIgnoreCase(episodeId)) {
                // Make new fragment to show this selection.
                detailsFragment = EpisodeDetailsFragment.newInstance(episodeId, true);

                // Execute a transaction, replacing any existing
                // fragment with this one inside the frame.
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_details, detailsFragment, "fragmentDetails");
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                ft.commit();
            }
        } else {
            Intent intent = new Intent();
            intent.setClass(getActivity(), EpisodeDetailsActivity.class);
            intent.putExtra(BaseColumns._ID, episodeId);
            startActivity(intent);
        }
    }

    private static class TraktFriendsLoader extends AsyncTaskLoader<List<UserProfile>> {

        private List<UserProfile> mFriends;

        public TraktFriendsLoader(Context context) {
            super(context);
        }

        @Override
        public List<UserProfile> loadInBackground() {
            if (ShareUtils.isTraktCredentialsValid(getContext())) {
                ServiceManager manager = null;
                try {
                    manager = Utils.getServiceManagerWithAuth(getContext(), false);
                } catch (Exception e) {
                    // TODO
                    return null;
                }

                try {
                    List<UserProfile> friends = manager.userService()
                            .friends(Utils.getTraktUsername(getContext())).fire();

                    // list watching now separately and first
                    List<UserProfile> friendsActivity = new ArrayList<UserProfile>();
                    for (UserProfile friend : friends) {
                        if (friend.watching != null && friend.watching.type == ActivityType.Episode) {
                            friendsActivity.add(friend);
                        }
                    }

                    // then include friends which have a watched episode no
                    // longer than 4 weeks in the past
                    // so a friend can appear as watching something right now
                    // and further down with the episode he watched before that
                    for (UserProfile friend : friends) {
                        for (ActivityItem activity : friend.watched) {

                            // is this an episode?
                            if (activity.type == ActivityType.Episode) {

                                // is this activity no longer than 4 weeks old?
                                if (activity.watched.getTime() > System.currentTimeMillis()
                                        - DateUtils.WEEK_IN_MILLIS * 4) {
                                    UserProfile clonedfriend = new UserProfile();
                                    clonedfriend.username = friend.username;
                                    clonedfriend.avatar = friend.avatar;

                                    List<ActivityItem> watchedclone = new ArrayList<ActivityItem>();
                                    watchedclone.add(activity);
                                    clonedfriend.watched = watchedclone;

                                    friendsActivity.add(clonedfriend);

                                    break;
                                }
                            }
                        }
                    }

                    return friendsActivity;
                } catch (TraktException te) {
                    // TODO
                    return null;
                } catch (ApiException ae) {
                    // TODO
                    return null;
                }
            } else {
                // TODO
                return null;
            }
        }

        /**
         * Called when there is new data to deliver to the client. The super
         * class will take care of delivering it; the implementation here just
         * adds a little more logic.
         */
        @Override
        public void deliverResult(List<UserProfile> friends) {
            if (isReset()) {
                // An async query came in while the loader is stopped. We
                // don't need the result.
                if (friends != null) {
                    onReleaseResources(friends);
                }
            }
            List<UserProfile> oldFriends = friends;
            mFriends = friends;

            if (isStarted()) {
                // If the Loader is currently started, we can immediately
                // deliver its results.
                super.deliverResult(friends);
            }

            if (oldFriends != null) {
                onReleaseResources(oldFriends);
            }
        }

        @Override
        protected void onStartLoading() {
            if (mFriends != null) {
                deliverResult(mFriends);
            } else {
                forceLoad();
            }
        }

        /**
         * Handles a request to stop the Loader.
         */
        @Override
        protected void onStopLoading() {
            // Attempt to cancel the current load task if possible.
            cancelLoad();
        }

        /**
         * Handles a request to cancel a load.
         */
        @Override
        public void onCanceled(List<UserProfile> friends) {
            super.onCanceled(friends);

            onReleaseResources(friends);
        }

        /**
         * Handles a request to completely reset the Loader.
         */
        @Override
        protected void onReset() {
            super.onReset();

            // Ensure the loader is stopped
            onStopLoading();

            // At this point we can release resources
            if (mFriends != null) {
                onReleaseResources(mFriends);
                mFriends = null;
            }
        }

        /**
         * Helper function to take care of releasing resources associated with
         * an actively loaded data set.
         */
        protected void onReleaseResources(List<UserProfile> apps) {
            // For a simple List<> there is nothing to do. For something
            // like a Cursor, we would close it here.
        }
    }

    private static class TraktFriendsAdapter extends ArrayAdapter<UserProfile> {
        private final ImageDownloader mImageDownloader;
        
        private final LayoutInflater mInflater;

        private final SharedPreferences mPrefs;

        public TraktFriendsAdapter(Context context) {
            super(context, R.layout.friend);
            mImageDownloader = ImageDownloader.getInstance(context);
            mPrefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        public void setData(List<UserProfile> data) {
            clear();
            if (data != null) {
                for (UserProfile userProfile : data) {
                    add(userProfile);
                }
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // A ViewHolder keeps references to children views to avoid
            // unneccessary calls to findViewById() on each row.
            ViewHolder holder;

            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.friend, null);

                holder = new ViewHolder();
                holder.name = (TextView) convertView.findViewById(R.id.name);
                holder.show = (TextView) convertView.findViewById(R.id.show);
                holder.episode = (TextView) convertView.findViewById(R.id.episode);
                holder.timestamp = (TextView) convertView.findViewById(R.id.timestamp);
                holder.avatar = (ImageView) convertView.findViewById(R.id.avatar);

                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            // TODO refactor!
            // Bind the data efficiently with the holder.
            UserProfile friend = getItem(position);
            
            holder.name.setText(friend.username);
            mImageDownloader.download(friend.avatar, holder.avatar);

            String show = "";
            String episode = "";
            String timestamp = "";
            if (friend.watching != null) {
                // look if this friend is watching something right now
                ActivityItemBase watching = friend.watching;
                switch (watching.type) {
                    case Episode:
                        show = watching.show.title;
                        String episodenumber = Utils.getEpisodeNumber(mPrefs,
                                String.valueOf(watching.episode.season),
                                String.valueOf(watching.episode.number));
                        episode = episodenumber + " " + watching.episode.title;
                        timestamp = getContext().getString(R.string.now);
                        break;
                }
            } else if (friend.watched != null) {
                // if not display the latest episode he watched
                List<ActivityItem> watched = friend.watched;
                ActivityItem latestShow = null;
                for (ActivityItem mediaEntity : watched) {
                    if (mediaEntity.type == ActivityType.Episode) {
                        latestShow = mediaEntity;
                        break;
                    }
                }

                if (latestShow != null) {
                    show = latestShow.show.title;
                    String episodenumber = Utils.getEpisodeNumber(mPrefs,
                            String.valueOf(latestShow.episode.season),
                            String.valueOf(latestShow.episode.number));
                    episode = episodenumber + " " + latestShow.episode.title;
                    timestamp = (String) DateUtils.getRelativeTimeSpanString(
                            latestShow.watched.getTime(), System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_ALL);
                }
            }

            holder.show.setText(show);
            holder.episode.setText(episode);
            holder.timestamp.setText(timestamp);

            return convertView;
        }

        static class ViewHolder {
            TextView name;

            TextView show;

            TextView episode;

            TextView timestamp;
            
            ImageView avatar;
        }
    }

    @Override
    public Loader<List<UserProfile>> onCreateLoader(int id, Bundle args) {
        return new TraktFriendsLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<List<UserProfile>> loader, List<UserProfile> data) {
        mAdapter.setData(data);

        if (isResumed()) {
            setListShown(true);
        } else {
            setListShownNoAnimation(true);
        }
    }

    @Override
    public void onLoaderReset(Loader<List<UserProfile>> loader) {
        mAdapter.setData(null);
    }

}
