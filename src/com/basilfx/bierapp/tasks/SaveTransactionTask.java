package com.basilfx.bierapp.tasks;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.apache.http.auth.AuthenticationException;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnKeyListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;

import com.basilfx.bierapp.BierAppApplication;
import com.basilfx.bierapp.R;
import com.basilfx.bierapp.actions.Action;
import com.basilfx.bierapp.callbacks.OnSaveActionListener;
import com.basilfx.bierapp.data.Connector;
import com.basilfx.bierapp.data.models.Transaction;
import com.basilfx.bierapp.database.DatabaseHelper;
import com.basilfx.bierapp.database.TransactionHelper;
import com.basilfx.bierapp.exceptions.RetriesExceededException;
import com.basilfx.bierapp.exceptions.UnexpectedData;
import com.basilfx.bierapp.exceptions.UnexpectedStatusCode;
import com.basilfx.bierapp.exceptions.UnexpectedUrl;
import com.basilfx.bierapp.utils.LogUtils;
import com.j256.ormlite.android.apptools.OpenHelperManager;

public class SaveTransactionTask extends DialogFragment {
	public static final String LOG_TAG = "SaveTransactionTask";
	
	public static final String FRAGMENT_TAG = "SaveTransactionTask";
	
	private OnSaveActionListener listener;
	
	private InnerTask task;
	
	private DatabaseHelper databaseHelper;
	
	private Connector connector;
	
	public static SaveTransactionTask newInstance(Transaction transaction) {
		SaveTransactionTask task = new SaveTransactionTask();
		
		Bundle arguments = new Bundle();
		arguments.putInt("transactionId", transaction.getId());
		task.setArguments(arguments);
		
		return task;
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		final ProgressDialog dialog = new ProgressDialog(this.getActivity());

		// Set title and message
		dialog.setMessage(this.getResources().getString(R.string.transactie_versturen));
		dialog.setIndeterminate(true);
		dialog.setCancelable(false);
		dialog.setCanceledOnTouchOutside(false);

		// Disable the back button
		dialog.setOnKeyListener(new OnKeyListener() {
			@Override
			public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
				return keyCode == KeyEvent.KEYCODE_BACK;
			}
		});

		// Done
		return dialog;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		this.listener = (OnSaveActionListener) activity;
	}

	@Override
	public void onDetach() {
		super.onDetach();
		this.listener = null;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Retain this fragment across configuration changes.
	    setRetainInstance(true);
		
	    // Create an API connector
	    this.connector = new Connector(BierAppApplication.getRemoteClient(), this.getHelper());
	    
	    // Create and start task
	    int transactionId = this.getArguments().getInt("transactionId");
	    
		this.task = new InnerTask();
		this.task.execute(transactionId);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (this.databaseHelper != null) {
            OpenHelperManager.releaseHelper();
        }
	}
	
	@Override
	public void onDestroyView() {
		if (this.getDialog() != null && this.getRetainInstance()) {
			this.getDialog().setDismissMessage(null);
		}
		
		super.onDestroyView();
	}
	
	protected DatabaseHelper getHelper() {
        if (this.databaseHelper == null) {
            this.databaseHelper = OpenHelperManager.getHelper(getActivity(), DatabaseHelper.class);
        }
        
        return this.databaseHelper;
    }
	
	private class InnerTask extends AsyncTask<Integer, Void, Integer> {
		
		private int newTransactionId;
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			Log.d(LOG_TAG, "Task started");
		}

		@Override
		protected Integer doInBackground(Integer... params) {
			// Retrieve transaction
			DatabaseHelper helper = SaveTransactionTask.this.getHelper();
			Transaction transaction = new TransactionHelper(helper).select()
				.whereIdEq(params[0])
				.whereRemoteIdEq(null)
				.first();
			
            // Send transaction to the server
            try {
                Transaction result = SaveTransactionTask.this.connector.saveTransaction(transaction);
                
                if (result != null) {
                    // Reload new user data
                	Log.d(LOG_TAG, "Loading users info");
                    SaveTransactionTask.this.connector.loadUserInfo();
                    
                    // Stats
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    
        			if (preferences.getBoolean("summary_show", true)) {
        				Calendar dayEnd = new GregorianCalendar();
        				dayEnd.setTimeInMillis(preferences.getLong("summary_day_end", System.currentTimeMillis()));
        				int days = Integer.parseInt(preferences.getString("summary_days", "1"));
        					
        				Log.d(LOG_TAG, "Loading stats");
        				SaveTransactionTask.this.connector.loadStats(dayEnd, days);
        			}
                    
                    // Save new transaction id
                    this.newTransactionId = result.getId();
                    
                    return Action.RESULT_OK;
                } else {
                    return Action.RESULT_ERROR_INTERNAL;
                }
            } catch (IOException e) {
                return LogUtils.logException(LOG_TAG, e, Action.RESULT_ERROR_CONNECTION);
            } catch (SQLException e) {
                return LogUtils.logException(LOG_TAG, e, Action.RESULT_ERROR_SQL);
            } catch (AuthenticationException e) {
                return LogUtils.logException(LOG_TAG, e, Action.RESULT_ERROR_AUTHENTICATION);
            } catch (UnexpectedStatusCode e) {
                return LogUtils.logException(LOG_TAG, e, Action.RESULT_ERROR_SERVER);
            } catch (UnexpectedData e) {
                return LogUtils.logException(LOG_TAG, e, Action.RESULT_ERROR_SERVER);
            } catch (RetriesExceededException e) {
                return LogUtils.logException(LOG_TAG, e, Action.RESULT_ERROR_SERVER);
            } catch (UnexpectedUrl e) {
            	return LogUtils.logException(LOG_TAG, e, Action.RESULT_ERROR_SERVER);
            }
		}
		
		@Override
        protected void onPostExecute(Integer result) {
            checkNotNull(result);
            Log.d(LOG_TAG, "Task finished with result " + result);
            
            // Inform listener
            if (SaveTransactionTask.this.listener != null) {
            	SaveTransactionTask.this.listener.onSaveActionResult(result, this.newTransactionId);
            } else {
            	Log.d(LOG_TAG, "Not attached to an activity");
            }
            
            // Hide dialog
            Dialog dialog = SaveTransactionTask.this.getDialog();
            
            if (dialog != null) {
            	dialog.dismiss();
            }
		}
	}
}