package xyz.klinker.messenger.shared.util;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;

import xyz.klinker.messenger.shared.R;
import xyz.klinker.messenger.shared.data.MimeType;
import xyz.klinker.messenger.shared.data.Settings;

public class PromotionUtils {

    private Context context;
    private SharedPreferences sharedPreferences;

    public PromotionUtils(Context context) {
        this.context = context;
        this.sharedPreferences = Settings.get(context).getSharedPrefs();
    }

    public void checkPromotions() {
        if (shouldAskForRating()) {
            askForRating();
        }
    }

    private boolean shouldAskForRating() {
        String pref = "install_time";
        long currentTime = System.currentTimeMillis();
        long installTime = sharedPreferences.getLong(pref, -1L);

        if (installTime == -1L) {
            // write the install time to now
            sharedPreferences.edit().putLong(pref, currentTime).apply();
        } else {
            if (currentTime - installTime > TimeUtils.TWO_WEEKS) {
                return sharedPreferences.getBoolean("show_rate_it", true);
            }
        }

        return false;
    }

    private void askForRating() {
        sharedPreferences.edit().putBoolean("show_rate_it", false)
                .apply();

        new AlertDialog.Builder(context)
                .setTitle(R.string.love_pulse_question)
                .setMessage(R.string.give_a_rating)
                .setPositiveButton(R.string.rate_it, (dialog, which) -> {
                    Uri uri = Uri.parse("market://details?id=" + context.getPackageName());
                    Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);

                    try {
                        context.startActivity(goToMarket);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(context, "Couldn't launch the Play Store!", Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton(R.string.share, (dialog, which) -> {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType(MimeType.TEXT_PLAIN);
                    share.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_promo));

                    context.startActivity(Intent.createChooser(share, context.getString(R.string.share)));
                }).show();
    }
}