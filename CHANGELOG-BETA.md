Change Log
==========

Version 2.3.2beta *(2011-11-22)*
--------------------------------

* Thrown the TVDb updater out the airlock (so soon): now SeriesGuide will update your show if it has not been for more than a week when pressing the update button.
* AutoUpdate (finally...): as updating now happens truly in the background (sorry, manual abort is gone for now, just drop your connection...) enabling this will press the update button for you at most once a day. You have to open the app though.
* The thresholds (7 days for updating, 11 hours before next auto-update) are subject to discussion.
* 'Update on Wi-Fi only' (enabled by default) will prevent the updater from doing anything if you don't have a Wi-Fi connection to the internet.

Version 2.3.1beta *(2011-11-19)*
--------------------------------

* Two more air time parsing schemas (e.g. '9:00PM' and '9PM' instead of quasi-standard '9:00 PM'), let me know if there are errors!
* Major cleanup, report any issues/broken features!
* Latest translations from crowdin.net. Now with Bulgarian thanks to Martin. Italian and Slovenian included for now despite low translation level.
* TVDb buttons on show info and episode page
* Editing of trakt credentials in settings
* Fixed broken background in episode pager

Version 2.3beta *(2011-11-06)*
--------------------------------

* You might need to clear your trakt.tv credentials if you encounter problems
* Revamped adding of shows (better trakt.tv integration, recommended and library only for logged in trakt users)
* Episode details shown in swipeable pager (only in non-dual-pane layout, yes, the background is broken)
* Shows will get updated by the incremental updater after at least every 7 days
* Fix crash when adding shows on certain HTC devices (Desire HD, Mytouch 4G, ...)
* Layout/Design fixes

Version 2.2.3beta *(2011-10-30)*
--------------------------------

* Clear old trakt credentials correctly

Version 2.2.2beta *(2011-10-30)*
--------------------------------

* Fix layouts on small tablet (large) devices
* Validate trakt.tv credentials
* Secure the trakt.tv password even better (you will have to reenter it again, sorry)
* Don’t rebuild the search table if nothing was updated

Version 2.2.1beta *(2011-10-18)*
--------------------------------

* Relayout widget as suggested by Allen
* Some bug fixes


Version 2.2beta *(2011-10-16)*
--------------------------------

* Tell which show is currently getting updated
* Dual pane layout for Upcoming on large+ devices
* New number format brought to you by dqdb via GitHub
* Small tweaks everywhere
* Layout improvements all over the app (spot the differences!), esp. on tablet/Google TV (large+) devices
* Display correct time stamps for US Central users
* Latest translations from crowdin (Danish now in again)

Version 2.1.5beta *(2011-10-02)*
--------------------------------

* Don't reload the show list on config changes (e.g. orientation changes): scrolling state is remembered again
* Image loader now checks faster for existing images and only downloads images up to 100KB in size (most are around 30K)
* Display confirmation message when adding show instantly after pressing the 'Add Show' button
* Latest translations (mlucas beefed up Dutch)

Version 2.1.4beta *(2011-10-01)*
--------------------------------

* Filter options for show list (replaces 'Hide watched shows' setting).
* Rearranged show list menu items to make room for filter.
* Latest translations from crowdin.
* Bug fixes and improvements.

Version 2.1.3beta *(2011-09-11)*
--------------------------------

* Use new trakt library release (better error handling)
* New show sorting: favorites by next episode
* Clean up images when deleting a show
* Store images in correct folder for beta users (you have to redownload them, sorry)
* Show first episode of season when using dual-pane layout
* Bugfixes, Improvements


Version 2.1.2beta *(2011-09-04)*
--------------------------------

* Please do an 'Update All' because of: Revert some time calc code that accidentially slipped into the last beta
* Better user communication when doing delta and full update (now called 'Update All')
* Use TVDB id instead of IMDb id to mark episodes as seen on trakt
* Don't require a touchscreen to use SeriesGuide (upcoming Google TV support)

Version 2.1.1beta *(2011-08-27)*
--------------------------------

* Always use a GridLayout for the show list.
* Tidy up settings.
* Change URLs to new webiste.
* Improvements for DeltaUpdate.
* Latest translations.