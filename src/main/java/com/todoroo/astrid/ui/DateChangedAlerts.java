/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

import com.todoroo.andlib.utility.DateUtilities;
import com.todoroo.astrid.data.Task;
import com.todoroo.astrid.gcal.GCalHelper;
import com.todoroo.astrid.repeats.RepeatTaskCompleteListener;
import com.todoroo.astrid.service.TaskService;
import com.todoroo.astrid.ui.DateAndTimeDialog.DateAndTimeDialogListener;
import com.todoroo.astrid.utility.Flags;

import org.tasks.R;
import org.tasks.injection.ForApplication;
import org.tasks.preferences.ActivityPreferences;

import javax.inject.Inject;

import static com.todoroo.andlib.utility.AndroidUtilities.atLeastGingerbread;
import static org.tasks.date.DateTimeUtils.newDate;

/**
 * Helper class that creates a dialog to confirm the results of a quick add markup
 * @author Sam
 *
 */
public class DateChangedAlerts {

    /** Preference key for how many of these helper dialogs we've shown */
    private static final String PREF_NUM_HELPERS_SHOWN = "pref_num_date_helpers"; //$NON-NLS-1$

    /** Preference key for whether or not we should show such dialogs */
    private static final int PREF_SHOW_HELPERS = R.string.p_showSmartConfirmation_key;

    /** Start showing the option to hide future notifs after this many confirmation dialogs */
    private static final int HIDE_CHECKBOX_AFTER_SHOWS = 3;

    private final Context context;
    private final ActivityPreferences preferences;

    @Inject
    public DateChangedAlerts(@ForApplication Context context, ActivityPreferences preferences) {
        this.context = context;
        this.preferences = preferences;
    }

    public void showRepeatTaskRescheduledDialog(final GCalHelper gcalHelper, final TaskService taskService, final Activity activity, final Task task,
            final long oldDueDate, final long newDueDate, final boolean lastTime) {
        if (!preferences.getBoolean(PREF_SHOW_HELPERS, true)) {
            return;
        }

        final Dialog d = new Dialog(activity, R.style.ReminderDialog);

        d.setContentView(R.layout.astrid_reminder_view);

        Button okButton = (Button) d.findViewById(R.id.reminder_complete);
        Button undoButton = (Button) d.findViewById(R.id.reminder_edit);

        Button keepGoing = (Button) d.findViewById(R.id.reminder_snooze);
        if (!lastTime) {
            keepGoing.setVisibility(View.GONE);
        } else {
            keepGoing.setText(R.string.repeat_keep_going);
            keepGoing.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    long startDate = 0;
                    DateAndTimeDialog picker = new DateAndTimeDialog(preferences, activity, startDate, R.layout.repeat_until_dialog, R.string.repeat_until_title);
                    picker.setDateAndTimeDialogListener(new DateAndTimeDialogListener() {
                        @Override
                        public void onDateAndTimeSelected(long date) {
                            d.dismiss();
                            task.setRepeatUntil(date);
                            RepeatTaskCompleteListener.rescheduleTask(context, gcalHelper, taskService, task, newDueDate);
                            Flags.set(Flags.REFRESH);
                        }
                    });
                    picker.show();
                }
            });
        }

        okButton.setText(android.R.string.ok);
        undoButton.setText(R.string.DLG_undo);

        int titleResource = lastTime ? R.string.repeat_rescheduling_dialog_title_last_time : R.string.repeat_rescheduling_dialog_title;
        ((TextView) d.findViewById(R.id.reminder_title)).setText(
                activity.getString(titleResource, task.getTitle()));

        String oldDueDateString = getRelativeDateAndTimeString(activity, oldDueDate);
        String newDueDateString = getRelativeDateAndTimeString(activity, newDueDate);
        String repeatUntilDateString = getRelativeDateAndTimeString(activity, task.getRepeatUntil());

        String encouragement = "";

        String speechBubbleText;
        if (lastTime) {
            speechBubbleText = activity.getString(R.string.repeat_rescheduling_dialog_bubble_last_time, repeatUntilDateString, encouragement);
        } else if (!TextUtils.isEmpty(oldDueDateString)) {
            speechBubbleText = activity.getString(R.string.repeat_rescheduling_dialog_bubble, encouragement, oldDueDateString, newDueDateString);
        } else {
            speechBubbleText = activity.getString(R.string.repeat_rescheduling_dialog_bubble_no_date, encouragement, newDueDateString);
        }

        ((TextView) d.findViewById(R.id.reminder_message)).setText(speechBubbleText);

        setupOkAndDismissButtons(d);
        setupHideCheckbox(d);

        undoButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                task.setDueDate(oldDueDate);
                task.setCompletionDate(0L);
                long hideUntil = task.getHideUntil();
                if (hideUntil > 0) {
                    task.setHideUntil(hideUntil - (newDueDate - oldDueDate));
                }
                taskService.save(task);
                Flags.set(Flags.REFRESH);
            }
        });

        setupDialogLayoutParams(activity, d);

        d.setOwnerActivity(activity);
        d.show();
    }

    private void setupOkAndDismissButtons(final Dialog d) {
        d.findViewById(R.id.reminder_complete).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        d.findViewById(R.id.dismiss).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
    }

    private void setupHideCheckbox(final Dialog d) {
        int numShows = preferences.getInt(PREF_NUM_HELPERS_SHOWN, 0);
        numShows++;
        if (numShows >= HIDE_CHECKBOX_AFTER_SHOWS) {
            CheckBox checkbox = (CheckBox) d.findViewById(R.id.reminders_should_show);
            checkbox.setVisibility(View.VISIBLE);
            checkbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    preferences.setBoolean(PREF_SHOW_HELPERS, !isChecked);
                }
            });
        }
        preferences.setInt(PREF_NUM_HELPERS_SHOWN, numShows);
    }

    private void setupDialogLayoutParams(Context context, Dialog d) {
        LayoutParams params = d.getWindow().getAttributes();
        params.width = LayoutParams.FILL_PARENT;
        params.height = LayoutParams.WRAP_CONTENT;
        Configuration config = context.getResources().getConfiguration();
        int size = config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
        if (atLeastGingerbread() && size == Configuration.SCREENLAYOUT_SIZE_XLARGE || size == Configuration.SCREENLAYOUT_SIZE_LARGE) {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            params.width = metrics.widthPixels / 2;
        }
        d.getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);

    }

    private String getRelativeDateAndTimeString(Context context, long date) {
        String dueString = date > 0 ? DateUtilities.getRelativeDay(context, date, false) : "";
        if(Task.hasDueTime(date)) {
            dueString = String.format("%s at %s", dueString, //$NON-NLS-1$
                    DateUtilities.getTimeString(context, newDate(date)));
        }
        return dueString;
    }
}
