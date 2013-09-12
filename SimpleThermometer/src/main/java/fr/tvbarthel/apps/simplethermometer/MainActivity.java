package fr.tvbarthel.apps.simplethermometer;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;

public class MainActivity extends FragmentActivity implements SharedPreferences.OnSharedPreferenceChangeListener,
		OpenWeatherMapParserAsyncTask.Listener {

	public static final String PREF_KEY_BACKGROUND_COLOR = "PrefKeyBackgroundColor";
	public static final String PREF_KEY_TEXT_COLOR = "PrefKeyTextColor";
	public static final String PREF_KEY_ICON_COLOR = "PrefKeyIconColor";
	public static final String PREF_KEY_LAST_TEMPERATURE_IN_CELCIUS = "PrefKeylastTemperatureInCelcius";
	public static final String PREF_KEY_LAST_UPDATE_TIME = "PrefKeyLastUpdateTime";

	public static final long UPDATE_INTERVAL_IN_MILLIS = 30 * 60 * 1000;
	public static final long UPDATE_INTERVAL_IN_MILLIS_MANUAL = 10 * 60 * 1000;

	private TextView mTextViewTemperature;
	private RelativeLayout mRelativeLayoutBackground;
	private ImageView mImageViewFair;
	private ImageView mImageViewChange;
	private ImageView mImageViewRain;
	private ImageView mImageViewStorm;

	private SharedPreferences mDefaultSharedPreferences;
	private OpenWeatherMapParserAsyncTask mOpenWeatherMapResultLoader;

	private Toast mTextToast;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mTextViewTemperature = (TextView) findViewById(R.id.textViewTemperature);
		mRelativeLayoutBackground = (RelativeLayout) findViewById(R.id.relativeLayout);
		mImageViewFair = (ImageView) findViewById(R.id.imageViewFair);
		mImageViewChange = (ImageView) findViewById(R.id.imageViewChange);
		mImageViewRain = (ImageView) findViewById(R.id.imageViewRain);
		mImageViewStorm = (ImageView) findViewById(R.id.imageViewStorm);

		mDefaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
	}

	@Override
	protected void onResume() {
		super.onResume();
		mDefaultSharedPreferences.registerOnSharedPreferenceChangeListener(this);
		setBackgroundColor();
		setTextColor();
		setIconColor();
		displayTemperature();

		refreshTemperature();

	}

	@Override
	protected void onPause() {
		super.onPause();
		mDefaultSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
		hideToast();
		if (mOpenWeatherMapResultLoader != null) {
			mOpenWeatherMapResultLoader.cancel(true);
			mOpenWeatherMapResultLoader = null;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_item_action_background_color:
				pickSharedPreferenceColor(PREF_KEY_BACKGROUND_COLOR);
				return true;
			case R.id.menu_item_action_text_color:
				pickSharedPreferenceColor(PREF_KEY_TEXT_COLOR);
				return true;
			case R.id.menu_item_action_icon_color:
				pickSharedPreferenceColor(PREF_KEY_ICON_COLOR);
				return true;
			case R.id.menu_item_action_manual_refresh:
				refreshTemperature(true);
				return true;
			case R.id.menu_item_action_about:
				displayAbout();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(PREF_KEY_BACKGROUND_COLOR)) {
			setBackgroundColor(sharedPreferences);
		} else if (key.equals(PREF_KEY_TEXT_COLOR)) {
			setTextColor(sharedPreferences);
		} else if (key.equals(PREF_KEY_ICON_COLOR)) {
			setIconColor(sharedPreferences);
		}
	}

	private void displayTemperature() {
		final String tempStr = new DecimalFormat("#.#").format(
				mDefaultSharedPreferences.getFloat(PREF_KEY_LAST_TEMPERATURE_IN_CELCIUS, 20));
		mTextViewTemperature.setText(String.format(getString(R.string.temperature_in_celcius), tempStr));
	}

	private void setIconColor() {
		setIconColor(mDefaultSharedPreferences);
	}

	private void setIconColor(SharedPreferences sharedPreferences) {
		final int iconColor = sharedPreferences.getInt(PREF_KEY_ICON_COLOR,
				getResources().getColor(R.color.white));
		mImageViewFair.setColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP);
		mImageViewChange.setColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP);
		mImageViewRain.setColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP);
		mImageViewStorm.setColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP);
	}

	private void setTextColor() {
		setTextColor(mDefaultSharedPreferences);
	}

	private void setTextColor(SharedPreferences sharedPreferences) {
		final int textColor = sharedPreferences.getInt(PREF_KEY_TEXT_COLOR,
				getResources().getColor(R.color.black));
		mTextViewTemperature.setTextColor(textColor);
	}

	private void setBackgroundColor() {
		setBackgroundColor(mDefaultSharedPreferences);
	}

	private void setBackgroundColor(SharedPreferences sharedPreferences) {
		mRelativeLayoutBackground.setBackgroundColor(sharedPreferences.getInt(PREF_KEY_BACKGROUND_COLOR,
				getResources().getColor(R.color.holo_blue)));
	}

	private void pickSharedPreferenceColor(String preferenceKey) {
		SharedPreferenceColorPickerDialogFragment.newInstance(preferenceKey,
				getResources().getStringArray(R.array.pref_color_list_names),
				getResources().getIntArray(R.array.pref_color_list_colors)).show(getSupportFragmentManager(), null);
	}

	private boolean isNetworkConnected() {
		final ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		final NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
		return networkInfo != null && networkInfo.isConnected();
	}

	private void makeTextToast(int stringId) {
		makeTextToast(getString(stringId));
	}

	private void makeTextToast(String message) {
		if (mTextToast != null) {
			mTextToast.cancel();
		}
		mTextToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
		mTextToast.show();
	}

	private void hideToast() {
		if (mTextToast != null) {
			mTextToast.cancel();
			mTextToast = null;
		}
	}


	private void refreshTemperature() {
		refreshTemperature(false);
	}

	private void refreshTemperature(boolean manualRefresh) {
		final long now = System.currentTimeMillis();
		final long lastUpdate = mDefaultSharedPreferences.getLong(PREF_KEY_LAST_UPDATE_TIME, 0);
		long updateInterval = UPDATE_INTERVAL_IN_MILLIS;
		if(manualRefresh) updateInterval = UPDATE_INTERVAL_IN_MILLIS_MANUAL;

		if (now - lastUpdate > updateInterval) {
			if (!isNetworkConnected()) {
				makeTextToast(R.string.error_message_network_not_connected);
			} else if (mOpenWeatherMapResultLoader == null){
				final LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
				final Criteria criteria = new Criteria();
				criteria.setAccuracy(Criteria.ACCURACY_COARSE);
				final String provider = locationManager.getBestProvider(criteria, true);
				final Location location  = locationManager.getLastKnownLocation(provider);
				if(location == null) {
					makeTextToast(R.string.error_message_no_location_found);
				}else {
					final double latitude = location.getLatitude();
					final double longitude = location.getLongitude();
					mOpenWeatherMapResultLoader = new OpenWeatherMapParserAsyncTask(this);
					mOpenWeatherMapResultLoader.execute(String.format(getResources().getString(R.string.url_open_weather_api), latitude, longitude));
				}
			}
		}
	}

	public void resetOpenWeatherMapLoader() {
		if(mOpenWeatherMapResultLoader != null) {
			mOpenWeatherMapResultLoader.cancel(true);
			mOpenWeatherMapResultLoader.setListener(null);
			mOpenWeatherMapResultLoader = null;
		}
	}

	private void displayAbout() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.about_title);
		builder.setCancelable(true);
		builder.setPositiveButton(R.string.alert_dialog_ok_button, null);
		final TextView description = new TextView(this);
		description.setAutoLinkMask(RESULT_OK);
		description.setMovementMethod(LinkMovementMethod.getInstance());
		final int paddingInPixelSize = getResources().getDimensionPixelSize(R.dimen.default_padding);
		description.setPadding(paddingInPixelSize, paddingInPixelSize, paddingInPixelSize, paddingInPixelSize);
		final SpannableString s =new SpannableString(getString(R.string.about_description));
		Linkify.addLinks(s, Linkify.WEB_URLS);
		description.setText(s);
		builder.setView(description);
		builder.create().show();
	}

	@Override
	public void onWeatherLoadingSuccess(OpenWeatherMapParserResult result) {
		if (result.getTemperatureValue() != null) {
			final SharedPreferences.Editor editor = mDefaultSharedPreferences.edit();
			editor.putFloat(PREF_KEY_LAST_TEMPERATURE_IN_CELCIUS, result.getTemperatureValue());
			editor.putLong(PREF_KEY_LAST_UPDATE_TIME, System.currentTimeMillis());
			editor.commit();
		}
		displayTemperature();
		resetOpenWeatherMapLoader();
	}

	@Override
	public void onWeatherLoadingFail(int stringResourceId) {
		makeTextToast(stringResourceId);
		displayTemperature();
		resetOpenWeatherMapLoader();
	}

	@Override
	public void onWeatherLoadingProgress(int progress) {
		mTextViewTemperature.setText(String.format(getString(R.string.message_loading_progress), progress));
	}

	@Override
	public void onWeatherLoadingCancelled() {
		displayTemperature();
	}
}