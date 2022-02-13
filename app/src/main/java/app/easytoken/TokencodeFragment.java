/*
 * TokencodeFragment: main screen showing current tokencode and token metadata
 *
 * This file is part of Easy Token
 * Copyright (c) 2014, Kevin Cernekee <cernekee@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package app.easytoken;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import us.berkovitz.stoken.SecurIdToken;

public class TokencodeFragment extends ArrayAdapter<TokenInfo> {
	public interface OnTokenListChangeHandler {
		public void onTokenListChanged();
	};

	public static final String TAG = "EasyToken";

	private boolean mNeedsPin = false;

	private final int resourceLayout;
	private final Context mContext;
	private final List<TokenView> mTokens = Collections.synchronizedList(new ArrayList<>());
	private final Handler mHandler = new Handler();
	private final List<TokenInfo> items;
	private final Runnable updateRemainingTimeRunnable = new Runnable() {
		@Override
		public void run() {
			synchronized (mTokens) {
				for (TokenView holder : mTokens) {
					holder.update();
				}
			}
		}
	};

	public TokencodeFragment(Context context, int resource, List<TokenInfo> items){
		super(context, resource, items);
		this.resourceLayout = resource;
		this.mContext = context;
		this.items = items;
		startUpdateTimer();
	}

	private void startUpdateTimer() {
		Timer tmr = new Timer();
		tmr.schedule(new TimerTask() {
			@Override
			public void run() {
				mHandler.post(updateRemainingTimeRunnable);
			}
		}, 1000, 1000);
	}

	@Override
    public View getView(int position, View converterView, ViewGroup parent) {

		TokenView tv;
    	View v = converterView;
    	if(v == null){
    		LayoutInflater vi;
    		vi = LayoutInflater.from(mContext);
    		v = vi.inflate(resourceLayout, null);
    		tv = new TokenView();
    		tv.view = v;
    		v.setTag(tv);
    		synchronized (mTokens){
    			mTokens.add(tv);
			}
		} else {
    		tv = (TokenView) v.getTag();
		}

    	tv.token = getItem(position);
    	tv.update();

    	return v;
    }

    private void finishPinDialog(String pin, TokenView tv) {
    	//setupPinUI(pin);

    	if (tv.token.token.pinRequired()) {
    		tv.lastUpdate = 0;
			tv.token.pin = (pin == null) ? "" : pin;
			tv.token.save();
		}
	}

    private void setupTextWatcher(AlertDialog d, final TextView tv) {
    	/* gray out the OK button until a sane-looking PIN is entered */
		final Button okButton = d.getButton(AlertDialog.BUTTON_POSITIVE);
		okButton.setEnabled(false);

		tv.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable arg0) {
				String s = tv.getText().toString();
				okButton.setEnabled(SecurIdToken.Companion.pinFormatOk(s));
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}
		});
    }

    private AlertDialog changePinDialog(TokenView token) {
    	AlertDialog d;

    	final TextView tv = new EditText(mContext);
		tv.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_TEXT_VARIATION_PASSWORD);
		tv.setTransformationMethod(PasswordTransformationMethod.getInstance());

    	AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
    		.setView(tv)
    		.setTitle(R.string.new_pin)
    		.setMessage(R.string.new_pin_message)
    		.setPositiveButton(R.string.change, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					finishPinDialog(tv.getText().toString().trim(), token);
				}
    		})
    		.setNeutralButton(R.string.no_pin, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					finishPinDialog(null, token);
				}
    		})
    		.setNegativeButton(R.string.cancel, null);
    	d = builder.create();
    	d.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				// back button -> cancel PIN change
			}
    	});
    	d.show();
    	setupTextWatcher(d, tv);

		return d;
    }

	private void renameDialog(TokenView token) {
		AlertDialog d;

		final TextView tv = new EditText(mContext);
		tv.setInputType(InputType.TYPE_CLASS_TEXT);

		AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
				.setView(tv)
				.setTitle(R.string.rename_token)
				//.setMessage(R.string.new_pin_message)
				.setPositiveButton(R.string.change, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						renameToken(tv.getText().toString().trim(), token);
					}
				})
				.setNegativeButton(R.string.cancel, null);
		d = builder.create();
		d.show();
	}

	private void confirmRemove(TokenView token) {
		AlertDialog d;

		AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
				.setTitle(R.string.remove_confirm)
				//.setMessage(R.string.new_pin_message)
				.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface arg0, int arg1) {
						token.token.delete();
						items.remove(token.token);
						TokencodeFragment.OnTokenListChangeHandler callback = (TokencodeFragment.OnTokenListChangeHandler)mContext;
						callback.onTokenListChanged();
					}
				})
				.setNegativeButton(R.string.no, null);
		d = builder.create();
		d.show();
	}

	private void renameToken(String name, TokenView token){
		token.token.name = name;
		token.token.save();
		token.mTokenName.setText(name);
	}

	private class TokenView {
		View view;
		TokenInfo token;
		long lastUpdate = 0;
		TextView mTokencode, mTokenName;
		String mRawTokencode = "";
		ProgressBar mProgressBar;
		String code = "", nextCode = "";

		private void onTokencodeUpdate(View parent, String tokencode, String nextTokencode, int secondsLeft) {
			mProgressBar.setProgress(secondsLeft - 1);
			mRawTokencode = tokencode;
			mTokencode.setText(TokencodeBackend.formatTokencode(mRawTokencode));
			writeStatusField(parent, R.id.next_tokencode, R.string.next_tokencode,
					TokencodeBackend.formatTokencode(nextTokencode));

			Calendar now = Calendar.getInstance();
			DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.LONG);
			df.setTimeZone(TimeZone.getTimeZone("GMT"));
			String gmt = df.format(now.getTime()).replaceAll(" GMT.*", "");
			writeStatusField(parent, R.id.gmt, R.string.gmt, gmt);
		}

		private void populateView(View v, TokenInfo token) {
			mTokenName = (TextView)v.findViewById(R.id.token_name);
			mTokencode = (TextView)v.findViewById(R.id.tokencode);
			mProgressBar = (ProgressBar)v.findViewById(R.id.progress_bar);
			TokenView thisPtr = this;

			Button copyButton = (Button)v.findViewById(R.id.copy_button);
			copyButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					ClipboardManager clipboard = (ClipboardManager)
							mContext.getSystemService(Context.CLIPBOARD_SERVICE);
					ClipData clip = ClipData.newPlainText("Tokencode", mRawTokencode);
					clipboard.setPrimaryClip(clip);
					Toast.makeText(mContext, R.string.copied_entry, Toast.LENGTH_SHORT).show();
				}
			});

			Button pinButton = (Button)v.findViewById(R.id.change_pin_button);
			pinButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					changePinDialog(thisPtr);
				}
			});

			Button tokenMenu = (Button)v.findViewById(R.id.token_menu);
			tokenMenu.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					PopupMenu popupMenu = new PopupMenu(mContext, tokenMenu);

					popupMenu.getMenuInflater().inflate(R.menu.token, popupMenu.getMenu());
					popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
						@Override
						public boolean onMenuItemClick(MenuItem item) {
							switch (item.getItemId()) {
								case R.id.action_rename:
									renameDialog(thisPtr);
									return true;
								case R.id.action_remove:
									confirmRemove(thisPtr);
									return true;
								default:
									return false;
							}
						}
					});
					// Showing the popup menu
					popupMenu.show();
				}
			});

			/* static fields */
			mTokenName.setText(token.name);
			mNeedsPin = token.pinRequired;
			pinButton.setEnabled(mNeedsPin);

			writeStatusField(v, R.id.token_sn, R.string.token_sn, token.token.getSerial());
			mProgressBar.setMax(token.token.tokenInterval() - 1);

			DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
			long exp = token.token.unixExpDate() * 1000L;

			/* show field in red if expiration is <= 2 weeks away */
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.DAY_OF_MONTH, 14);
			writeStatusField(v, R.id.exp_date, R.string.exp_date, df.format(exp), cal.getTimeInMillis() >= exp);
			writeStatusField(v, R.id.dev_id, R.string.dev_id, TokenInfo.getDeviceId());
		}

		private void writeStatusField(View parent, int id, int header_res, String value, boolean warn) {
			String html = "<b>" + TextUtils.htmlEncode(mContext.getString(header_res)) + "</b><br>";
			value = TextUtils.htmlEncode(value);
			if (warn) {
				/*
				 * No CSS.  See:
				 * http://commonsware.com/blog/Android/2010/05/26/html-tags-supported-by-textview.html
				 */
				html += "<font color=\"red\"><b>" + value + "</b></font>";
			} else {
				html += value;
			}
			TextView tv = (TextView)parent.findViewById(id);
			tv.setText(Html.fromHtml(html));
		}

		private void writeStatusField(View parent, int id, int header_res, String value) {
			writeStatusField(parent, id, header_res, value, false);
		}

		private void setupPinUI(View parent, String s) {
			int res;
			boolean warn = false;

			if (!mNeedsPin) {
				res = R.string.not_required;
			} else if (s == null || s.isEmpty()) {
				warn = true;
				res = R.string.no;
			} else {
				res = R.string.yes;
			}

			writeStatusField(parent, R.id.using_pin, R.string.using_pin, mContext.getString(res), warn);
		}

		public void update(){
			// depends on mBackend
			populateView(view, token);

			// depends on mView from populateView()
			setupPinUI(view, token.pin);

			Calendar now = Calendar.getInstance();
			int second = now.get(Calendar.SECOND);
			int interval = token.token.tokenInterval();

			if(interval == 30)
				now.set(Calendar.SECOND, second >= 30 ? 30 : 0);
			else
				now.set(Calendar.SECOND, 0);

			long t = now.getTimeInMillis() / 1000;
			if(t != lastUpdate){
				String pin = !token.pin.equals("") ? token.pin : "0000";
				code = token.token.computeTokenCode(t, pin);
				nextCode = token.token.computeTokenCode(t + interval, pin);
				lastUpdate = t;
			}
			onTokencodeUpdate(view, code, nextCode, interval - (second % interval));
		}
	}
}
