package net.osmand.plus.voice;

import android.content.Context;
import android.media.AudioManager;

import net.osmand.IndexConstants;
import net.osmand.PlatformUtil;
import net.osmand.StateChangedListener;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.api.AudioFocusHelper;
import net.osmand.plus.helpers.enums.MetricsConstants;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.settings.backend.OsmandSettings;

import org.apache.commons.logging.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import alice.tuprolog.InvalidLibraryException;
import alice.tuprolog.InvalidTheoryException;
import alice.tuprolog.NoSolutionException;
import alice.tuprolog.Number;
import alice.tuprolog.Prolog;
import alice.tuprolog.SolveInfo;
import alice.tuprolog.Struct;
import alice.tuprolog.Term;
import alice.tuprolog.Theory;
import alice.tuprolog.Var;

public abstract class BaseCommandPlayer implements CommandPlayer, StateChangedListener<ApplicationMode> {

	private static final Log log = PlatformUtil.getLog(BaseCommandPlayer.class);

	protected OsmandApplication app;
	protected File voiceDir;
	protected Prolog prologSystem;
	protected static final String P_VERSION = "version";
	protected static final String P_RESOLVE = "resolve";

	public static final String A_LEFT = "left";
	public static final String A_LEFT_SH = "left_sh";
	public static final String A_LEFT_SL = "left_sl";
	public static final String A_LEFT_KEEP = "left_keep";
	public static final String A_RIGHT = "right";
	public static final String A_RIGHT_SH = "right_sh";
	public static final String A_RIGHT_SL = "right_sl";
	public static final String A_RIGHT_KEEP = "right_keep";

	protected static final String DELAY_CONST = "delay_";
	/** Must be sorted array! */
	private int[] sortedVoiceVersions;
	private static AudioFocusHelper mAudioFocusHelper;
	protected String language = "";
	protected int streamType;
	private static int currentVersion;
	private ApplicationMode applicationMode;


	protected BaseCommandPlayer(OsmandApplication app, ApplicationMode applicationMode,
	                            String voiceProvider, String configFile,
	                            int[] sortedVoiceVersions) throws CommandPlayerException {
		this.app = app;
		this.sortedVoiceVersions = sortedVoiceVersions;
		this.applicationMode = applicationMode;
		long time = System.currentTimeMillis();
		this.app = app;

		this.streamType = app.getSettings().AUDIO_MANAGER_STREAM.getModeValue(applicationMode);
		initVoiceDir(voiceProvider);
		if (voiceDir != null && (MediaCommandPlayerImpl.isMyData(voiceDir) || TTSCommandPlayerImpl.isMyData(voiceDir))) {
			if (log.isInfoEnabled()) {
				log.info("Initializing prolog system : " + (System.currentTimeMillis() - time));
			}
			try {
				prologSystem = new Prolog(getLibraries());
			} catch (InvalidLibraryException e) {
				log.error("Initializing error", e);
				throw new RuntimeException(e);
			}
			init(voiceProvider, app.getSettings(), configFile);
			final Term langVal = solveSimplePredicate("language");
			if (langVal instanceof Struct) {
				language = ((Struct) langVal).getName();
			}
		} else {
			language = voiceProvider.replace(IndexConstants.VOICE_PROVIDER_SUFFIX, "")
					.replace("-formal", "").replace("-casual", "");
		}
	}

	public ApplicationMode getApplicationMode() {
		return applicationMode;
	}

	public String getLanguage() {
		return language;
	}

	public String[] getLibraries(){
		return new String[] { "alice.tuprolog.lib.BasicLibrary",
					"alice.tuprolog.lib.ISOLibrary"/*, "alice.tuprolog.lib.IOLibrary"*/};
	}

	@Override
	public void stateChanged(ApplicationMode change) {
		app.runInUIThread(() -> {
			if (prologSystem != null) {
				try {
					prologSystem.getTheoryManager().retract(new Struct("appMode", new Var()));
				} catch (Exception e) {
					log.error("Retract error: ", e);
				}
				prologSystem.getTheoryManager()
						.assertA(
								new Struct("appMode", new Struct(app.getSettings().APPLICATION_MODE.get().getStringKey()
										.toLowerCase())), true, "", true);
			}
		});
	}
	
	private void init(String voiceProvider, OsmandSettings settings, String configFile) throws CommandPlayerException {
		prologSystem.clearTheory();

		if (voiceDir != null) {
			long time = System.currentTimeMillis();
			boolean wrong = false;
			try {
				InputStream config;
				config = new FileInputStream(new File(voiceDir, configFile));
				MetricsConstants mc = settings.METRIC_SYSTEM.get();
				settings.APPLICATION_MODE.addListener(this);
				prologSystem.getTheoryManager()
				.assertA(
						new Struct("appMode", new Struct(app.getSettings().APPLICATION_MODE.get().getStringKey()
								.toLowerCase())), true, "", true);
				prologSystem.addTheory(new Theory("measure('"+mc.toTTSString()+"')."));
				prologSystem.addTheory(new Theory(config));
				config.close();
			} catch (InvalidTheoryException | IOException e) {
				log.error("Loading voice config exception " + voiceProvider, e);
				wrong = true;
			}
			if (wrong) {
				throw new CommandPlayerException(app.getString(R.string.voice_data_corrupted));
			} else {
				Term val = solveSimplePredicate(P_VERSION);
				if (!(val instanceof Number) ||  Arrays.binarySearch(sortedVoiceVersions,((Number)val).intValue()) < 0) {
					throw new CommandPlayerException(app.getString(R.string.voice_data_not_supported));
				}
				currentVersion = ((Number)val).intValue();
			}
			if (log.isInfoEnabled()) {
				log.info("Initializing voice subsystem  " + voiceProvider + " : " + (System.currentTimeMillis() - time));
			}
		}
	}

	private void initVoiceDir(String voiceProvider) throws CommandPlayerException {
		if (voiceProvider != null) {
			File parent = app.getAppPath(IndexConstants.VOICE_INDEX_DIR);
			voiceDir = new File(parent, voiceProvider);
			if (!voiceDir.exists()) {
				voiceDir = null;
				throw new CommandPlayerException(
						app.getString(R.string.voice_data_unavailable));
			}
		}
	}

	protected Term solveSimplePredicate(String predicate) {
		Term val = null;
		Var v = new Var("MyVariable");
		SolveInfo s = prologSystem.solve(new Struct(predicate, v));
		if (s.isSuccess()) {
			prologSystem.solveEnd();
			try {
				val = s.getVarValue(v.getName());
			} catch (NoSolutionException e) {
			}
		}
		return val;
	}

	@Override
	public List<String> execute(List<Struct> listCmd){
		Struct list = new Struct(listCmd.toArray(new Term[0]));
		Var result = new Var("RESULT");
		List<String> files = new ArrayList<String>();
		if(prologSystem == null) {
			return files;
		}
		if (log.isInfoEnabled()) {
			log.info("Query speak files " + listCmd);
		}
		SolveInfo res = prologSystem.solve(new Struct(P_RESOLVE, list, result));
		
		if (res.isSuccess()) {
			try {
				prologSystem.solveEnd();	
				Term solution = res.getVarValue(result.getName());
				
				Iterator<?> listIterator = ((Struct) solution).listIterator();
				while(listIterator.hasNext()){
					Object term = listIterator.next();
					if(term instanceof Struct){
						files.add(((Struct) term).getName());
					}
				}
				
			} catch (NoSolutionException e) {
			}
		}
		if (log.isInfoEnabled()) {
			log.info("Speak files " + files);
		}
		return files;
	}
	
	public static int getCurrentVersion() {
		return currentVersion;
	}
	
	@Override
	public String getCurrentVoice() {
		if (voiceDir == null) {
			return null;
		}
		return voiceDir.getName();
	}

	@Override
	public CommandBuilder newCommandBuilder() {
		return new CommandBuilder(this);
	}

	@Override
	public void clear() {
		if(app != null && app.getSettings() != null) {
			app.getSettings().APPLICATION_MODE.removeListener(this);
		}
		abandonAudioFocus();
		app = null;
		prologSystem = null;
	}

	@Override
	public void updateAudioStream(int streamType) {
		this.streamType = streamType;
	}

	protected synchronized void requestAudioFocus() {
		log.debug("requestAudioFocus");
		mAudioFocusHelper = getAudioFocus();
		if (mAudioFocusHelper != null && app != null) {
			boolean audioFocusGranted = mAudioFocusHelper.requestAudFocus(app, applicationMode, streamType);
			if (audioFocusGranted && app.getSettings().AUDIO_MANAGER_STREAM.getModeValue(applicationMode) == 0) {
				toggleBtSco(true);
			}
		}
	}

	private AudioFocusHelper getAudioFocus() {
		try {
			return new net.osmand.plus.api.AudioFocusHelperImpl();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}
	
	protected synchronized void abandonAudioFocus() {
		log.debug("abandonAudioFocus");
		if ((app != null && app.getSettings().AUDIO_MANAGER_STREAM.getModeValue(applicationMode) == 0)
				|| btScoStatus) {
			toggleBtSco(false);
		}
		if (app != null && mAudioFocusHelper != null) {
			mAudioFocusHelper.abandonAudFocus(app, applicationMode, streamType);
		}
		mAudioFocusHelper = null;
	}

	public static boolean btScoStatus = false;

	// This only needed for init debugging in TestVoiceActivity:
	public static String btScoInit = "-";

	private synchronized boolean toggleBtSco(boolean on) {
	// Hardy, 2016-07-03: Establish a low quality BT SCO (Synchronous Connection-Oriented) link to interrupt e.g. a car stereo FM radio
		if (on) {
			try {
				AudioManager mAudioManager = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
				if (mAudioManager == null || !mAudioManager.isBluetoothScoAvailableOffCall()) {
					btScoInit = "Reported not available.";
					return false;
				}
				mAudioManager.setMode(0);
				mAudioManager.startBluetoothSco();
				mAudioManager.setBluetoothScoOn(true);
				mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
				btScoStatus = true;
			} catch (Exception e) {
				System.out.println("Exception starting BT SCO " + e.getMessage() );
				btScoStatus = false;
				btScoInit = "Available, but not initialized.\n(" + e.getMessage() + ")";
				return false;
			}
			btScoInit = "Available, initialized OK.";
			return true;
		} else {
			AudioManager mAudioManager = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
			if (mAudioManager == null) {
				return false;
			}
			mAudioManager.setBluetoothScoOn(false);
			mAudioManager.stopBluetoothSco();
			mAudioManager.setMode(AudioManager.MODE_NORMAL);
			btScoStatus = false;
			return true;
		}
	}
}