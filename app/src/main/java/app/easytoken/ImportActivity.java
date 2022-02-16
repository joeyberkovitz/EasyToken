/*
 * ImportActivity: coordinates importing a new token
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
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import kotlin.Unit;
import us.berkovitz.stoken.SecurIdToken;
import us.berkovitz.stoken.TokenGUID;

public class ImportActivity extends AppCompatActivity
		implements ImportMethodFragment.OnImportMethodSelectedListener,
		           ImportManualEntryFragment.OnManualEntryDoneListener,
		           ImportUnlockFragment.OnUnlockDoneListener,
		           ImportConfirmFragment.OnConfirmDoneListener {

	public static final String TAG = "EasyToken";

	private static final String PFX = "app.easytoken.";

	private static final int STEP_NONE = 0;
	private static final int STEP_METHOD = 1;
	private static final int STEP_URI_INSTRUCTIONS = 2;
	private static final int STEP_IMPORT_TOKEN = 3;
	private static final int STEP_MANUAL_ENTRY = 4;
	private static final int STEP_ERROR = 5;
	private static final int STEP_UNLOCK_TOKEN = 6;
	private static final int STEP_CONFIRM_IMPORT = 7;
	private static final int STEP_DONE = 8;

	private AlertDialog mDialog;

	/* these get saved if the Activity is destroyed and re-created */
	private int mStep;
	private String mInputMethod;
	private String mUri;
	private String mGuessedDevID = "";
	private String mErrorType;
	private String mErrorData;

	private static final String STATE_STEP = PFX + "step";
	private static final String STATE_INPUT_METHOD = PFX + "input_method";
	private static final String STATE_URI = PFX + "uri";
	private static final String STATE_GUESSED_DEV_ID = PFX + "guessed_dev_id";
	private static final String STATE_ERROR_TYPE = PFX + "error_type";
	private static final String STATE_ERROR_DATA = PFX + "error_data";

	ActivityResultLauncher<Unit> mQrScanStartResult = registerForActivityResult(new QRResult(),
			new ActivityResultCallback<String>() {
				@Override
				public void onActivityResult(String result) {
					if(result != null){
						tryImport(result);
					}
				}
			});

	ActivityResultLauncher<String[]> mFileSelectResult = registerForActivityResult(
			new ActivityResultContracts.OpenDocument(), new ActivityResultCallback<Uri>() {
				@Override
				public void onActivityResult(Uri result) {
					if(result != null){
						tryImport(Misc.readStringFromUri(getBaseContext(), result));
					}
				}
			});

	@Override
	public void onCreate(Bundle b) {
		super.onCreate(b);

		if (b != null) {
			mStep = b.getInt(STATE_STEP);
			mInputMethod = b.getString(STATE_INPUT_METHOD);
			mUri = b.getString(STATE_URI);
			mGuessedDevID = b.getString(STATE_GUESSED_DEV_ID);
			mErrorType = b.getString(STATE_ERROR_TYPE);
			mErrorData = b.getString(STATE_ERROR_DATA);
		} else {
			Intent i = this.getIntent();
			if (i != null) {
				Uri uri = i.getData();
				if (uri != null) {
					mUri = uri.toString();
				}
			}

			handleImportStep();
		}
	}

	@Override
	protected void onPause() {
		if (mDialog != null) {
			mDialog.dismiss();
			mDialog = null;
		}
		super.onPause();
	}

	@Override
	protected void onSaveInstanceState(Bundle b) {
		super.onSaveInstanceState(b);
		b.putInt(STATE_STEP, mStep);
		b.putString(STATE_INPUT_METHOD, mInputMethod);
		b.putString(STATE_URI, mUri);
		b.putString(STATE_GUESSED_DEV_ID, mGuessedDevID);
		b.putString(STATE_ERROR_TYPE, mErrorType);
		b.putString(STATE_ERROR_DATA, mErrorData);
	}

	private void showFrag(Fragment f, boolean animate) {
		FragmentTransaction ft = getFragmentManager().beginTransaction();

		if (animate) {
			ft.setCustomAnimations(R.animator.fragment_slide_left_enter, R.animator.fragment_slide_left_exit);
		}

		ft.replace(android.R.id.content, f).commit();
	}

	private void showError(String errCode, String errData) {
		mStep = STEP_ERROR;
		mErrorType = errCode;
		mErrorData = errData;
		handleImportStep();
	}

	private SecurIdToken importToken(String data, boolean decrypt) {
		Uri uri = Uri.parse(data);
		String path = uri.getPath();
		boolean isFile = false;

		if (path != null &&
			("file".equals(uri.getScheme()) || "content".equals(uri.getScheme()))) {
			/*
			 * Arguably we shouldn't take file:// URIs from QR codes,
			 * and maybe we should be more careful about what we accept
			 * from other apps too
			 */
			isFile = true;
			data = Misc.readStringFromUri(this, uri);
			if (data == null) {
				showError(ImportInstructionsFragment.INST_FILE_ERROR, path);
				return null;
			}
		}

		SecurIdToken res;
		try {
			Log.d(TAG, "Loading token");
			res = SecurIdToken.Companion.importString(data, false);
		} catch (Exception exc) {
			Log.e(TAG, "error loading token", exc);
			showError(ImportInstructionsFragment.INST_BAD_TOKEN, isFile ? "" : data);
			return null;
		}

		if(decrypt){
			try {
				res.decryptSeed("", "");
			} catch (Exception exc){
				showError(ImportInstructionsFragment.INST_BAD_TOKEN, isFile ? "" : data);
				return null;
			}
		}

		return res;
	}

	private boolean guessDevID(SecurIdToken token, String id) {
		// This checks the devid hash, but on v2 tokens it is only 15 bits and is
		// prone to collisions...
		if (!token.checkDevId(id)) {
			return false;
		}

		// ...so, for passwordless v2 tokens, we can perform a test decrypt to
		// rule out that possibility.
		// Passworded v2 tokens are trickier because they could pass
		// lib.checkDevId() with a hash collision, and we can't check the other
		// hash until both the devid AND password are correct.
		if (token.passRequired()) {
			mGuessedDevID = id;
			return true;
		} else {
			try {
				token.decryptSeed("", id);
				mGuessedDevID = id;
				return true;
			} catch (Exception ignored) {}
		}
		return false;
	}

	private boolean manualDevID(SecurIdToken token) {
		if (!token.devIdRequired()) {
			// token doesn't require a device ID at all
			return false;
		}

		if (guessDevID(token, TokenInfo.getDeviceId())) {
			// guessed correctly (probably) - save it
			return false;
		}

		for (TokenGUID g : TokenGUID.values()) {
			// Try known class GUIDs for Android, iPhone, Blackberry, ...
			if (guessDevID(token, g.getGuid())) {
				return false;
			}
		}

		// no luck, revert back to manual entry
		mGuessedDevID = "";
		return true;
	}

	private void writeNewToken(SecurIdToken token) {
		TokenInfo info = new TokenInfo(token, null);
		info.save();
	}

	private void handleImportStep() {
		Fragment f;
		boolean animate = true;

		if (mStep == STEP_NONE) {
			/* initial entry */
			mStep = (mUri == null) ? STEP_METHOD : STEP_IMPORT_TOKEN;
			animate = false;
		}

		if (mStep == STEP_METHOD) {
			showFrag(new ImportMethodFragment(), animate);
		} else if (mStep == STEP_URI_INSTRUCTIONS) {
			Bundle b = new Bundle();
			b.putString(ImportInstructionsFragment.ARG_INST_TYPE,
					    ImportInstructionsFragment.INST_URI_HELP);

			f = new ImportInstructionsFragment();
			f.setArguments(b);
			showFrag(f, animate);
		} else if (mStep == STEP_MANUAL_ENTRY) {
			showFrag(new ImportManualEntryFragment(), animate);
		} else if (mStep == STEP_IMPORT_TOKEN) {
			SecurIdToken token = importToken(mUri, false);
			if (token == null) {
				/* mStep has already been advanced to an error state */
				return;
			}
			if (manualDevID(token) || token.passRequired()) {
				mStep = STEP_UNLOCK_TOKEN;
			} else {
				try{
					token.decryptSeed("", mGuessedDevID);
				} catch (Exception e){
					showError(ImportInstructionsFragment.INST_BAD_TOKEN, mUri);
					return;
				}
				mUri = token.encodeToken("", "", 2);
				mStep = STEP_CONFIRM_IMPORT;
			}
			handleImportStep();
		} else if (mStep == STEP_ERROR) {
			Bundle b = new Bundle();
			b.putString(ImportInstructionsFragment.ARG_INST_TYPE, mErrorType);
			b.putString(ImportInstructionsFragment.ARG_TOKEN_DATA, mErrorData);

			f = new ImportInstructionsFragment();
			f.setArguments(b);
			showFrag(f, animate);
		} else if (mStep == STEP_UNLOCK_TOKEN) {
			SecurIdToken token = importToken(mUri, false);
			if (token == null) {
				/* mStep has already been advanced to an error state */
				return;
			}
			Bundle b = new Bundle();
			b.putString(ImportUnlockFragment.ARG_DEFAULT_DEVID, mGuessedDevID);
			b.putBoolean(ImportUnlockFragment.ARG_REQUEST_PASS, token.passRequired());
			b.putBoolean(ImportUnlockFragment.ARG_REQUEST_DEVID, token.devIdRequired());

			/*
			 * NOTE: The PIN is not captured here.  isPINRequired() may return false if we're
			 * handling an encrypted v3 token, because the PIN flag is in the encrypted
			 * payload.
			 */
			b.putBoolean(ImportUnlockFragment.ARG_REQUEST_PIN, false);

			f = new ImportUnlockFragment();
			f.setArguments(b);
			showFrag(f, animate);

		} else if (mStep == STEP_CONFIRM_IMPORT) {
			SecurIdToken token = importToken(mUri, true);
			if (token == null) {
				/* mStep has already been advanced to an error state */
				return;
			}

			Bundle b = new Bundle();
			b.putString(ImportConfirmFragment.ARG_NEW_TOKEN, mUri);

			f = new ImportConfirmFragment();
			f.setArguments(b);
			showFrag(f, animate);

		} else if (mStep == STEP_DONE) {
			finish();
		}
	}

	@Override
	public void onImportMethodSelected(String method) {
		mInputMethod = method;

		switch (method) {
			case "uri":
				mStep = STEP_URI_INSTRUCTIONS;
				handleImportStep();
				break;
			case "qr":
				mQrScanStartResult.launch(Unit.INSTANCE);
				break;
			case "browse":
				mFileSelectResult.launch(new String[]{"*/*"});
				break;
			case "manual":
				mStep = STEP_MANUAL_ENTRY;
				handleImportStep();
				break;
		}
	}

	private void tryImport(String s) {
		mStep = STEP_IMPORT_TOKEN;
		mUri = s;
		handleImportStep();
	}

	@Override
	public void onManualEntryDone(String token) {
		tryImport(token);
	}

	private AlertDialog errorDialog(int titleRes, int messageRes) {
		final AlertDialog d;

		d = new AlertDialog.Builder(this)
			.setTitle(titleRes)
			.setMessage(messageRes)
			.setPositiveButton(R.string.ok, null)
			.show();
		return d;
	}

	@Override
	public void onUnlockDone(String pass, String devid, String pin) {
		SecurIdToken token = importToken(mUri, false);
		if (token == null) {
			/* mStep has already been advanced to an error state */
			return;
		}

		try {
			token.decryptSeed(pass, devid);
		} catch (Exception e){
			int resId = R.string.general_failure;

			if (token.devIdRequired() && !token.checkDevId(devid)) {
				resId = R.string.devid_bad;
			} else if (token.passRequired()) {
				resId = R.string.pass_bad;
			}
			mDialog = errorDialog(R.string.unable_to_process_token, resId);
			return;
		}

		mUri = token.encodeToken("", "", 2);
		mStep = STEP_CONFIRM_IMPORT;
		handleImportStep();
	}

	@Override
	public void onConfirmDone(boolean accepted) {
		if (accepted) {
			SecurIdToken lib = importToken(mUri, true);
			if (lib == null) {
				/* mStep has already been advanced to an error state */
				return;
			}
			writeNewToken(lib);
		}
		mStep = STEP_DONE;
		handleImportStep();
	}
}
