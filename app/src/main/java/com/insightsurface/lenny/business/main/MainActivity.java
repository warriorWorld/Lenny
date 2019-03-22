package com.insightsurface.lenny.business.main;

import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.insightsurface.lenny.R;
import com.insightsurface.lenny.business.base.BaseActivity;
import com.insightsurface.lenny.configure.Configure;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends BaseActivity implements View.OnClickListener, TextToSpeech.OnInitListener {
    private TextView stateTv, conversationTv;
    private Button startBtn, stopBtn;
    private final String TAG = "RecordManager";
    private MediaRecorder mMediaRecorder;
    public static final int MAX_LENGTH = 1000 * 60 * 10;// 最大录音时长1000*60*10;
    private File file;
    private long startTime;
    private long endTime;
    private boolean recording = false;
    private int db;
    private String conversationContent = "";
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        file = new File(Environment
                .getExternalStorageDirectory().getAbsolutePath() + "/Lenny/record.3gp");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        initTTS();
        initUI();
    }

    private void initTTS() {
        tts = new TextToSpeech(this, this); // 参数Context,TextToSpeech.OnInitListener
    }

    private void initUI() {
        stateTv = (TextView) findViewById(R.id.state_tv);
        conversationTv = findViewById(R.id.conversation_tv);
        startBtn = findViewById(R.id.start_btn);
        stopBtn = findViewById(R.id.stop_btn);
        stopBtn.setOnClickListener(this);
        startBtn.setOnClickListener(this);
    }


    /**
     * 开始录音 使用amr格式
     *
     * @return
     */
    public void startRecord() {
        // 开始录音
        /* ①Initial：实例化MediaRecorder对象 */
        if (mMediaRecorder == null)
            mMediaRecorder = new MediaRecorder();
        try {
            /* ②setAudioSource/setVedioSource */
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);// 设置麦克风
            /*
             * ②设置输出文件的格式：THREE_GPP/MPEG-4/RAW_AMR/Default THREE_GPP(3gp格式
             * ，H263视频/ARM音频编码)、MPEG-4、RAW_AMR(只支持音频且音频编码要求为AMR_NB)
             */
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
            /* ②设置音频文件的编码：AAC/AMR_NB/AMR_MB/Default 声音的（波形）的采样 */
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
            /* ③准备 */
            mMediaRecorder.setOutputFile(file.getAbsolutePath());
            mMediaRecorder.setMaxDuration(MAX_LENGTH);
            mMediaRecorder.prepare();
            /* ④开始 */
            mMediaRecorder.start();
            // AudioRecord audioRecord.
            /* 获取开始时间* */
            startTime = System.currentTimeMillis();
            // pre=mMediaRecorder.getMaxAmplitude();
            updateMicStatus();
            Log.i("ACTION_START", "startTime" + startTime);
        } catch (IllegalStateException e) {
            Log.i(TAG,
                    "call startAmr(File mRecAudioFile) failed!"
                            + e.getMessage());
        } catch (IOException e) {
            Log.i(TAG,
                    "call startAmr(File mRecAudioFile) failed!"
                            + e.getMessage());
        }

    }

    /**
     * 停止录音
     */
    public long stopRecord() {
        if (mMediaRecorder == null)
            return 0L;
        recording = false;
        replyCount = 0;
        endTime = System.currentTimeMillis();
        Log.i("ACTION_END", "endTime" + endTime);
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        mMediaRecorder.release();
        mMediaRecorder = null;
        Log.i("ACTION_LENGTH", "Time" + (endTime - startTime));
        return endTime - startTime;
    }

    private final Handler mHandler = new Handler();
    private Runnable mUpdateMicStatusTimer = new Runnable() {
        public void run() {
            updateMicStatus();
        }
    };

    /**
     * 更新话筒状态 分贝是也就是相对响度 分贝的计算公式K=20lg(Vo/Vi) Vo当前振幅值 Vi基准值为600：我是怎么制定基准值的呢？ 当20
     * * Math.log10(mMediaRecorder.getMaxAmplitude() / Vi)==0的时候vi就是我所需要的基准值
     * 当我不对着麦克风说任何话的时候，测试获得的mMediaRecorder.getMaxAmplitude()值即为基准值。
     * Log.i("mic_", "麦克风的基准值：" + mMediaRecorder.getMaxAmplitude());前提时不对麦克风说任何话
     */
    private int BASE = 600;
    private int SPACE = 200;// 间隔取样时间
    private int silentCount = 0;
    private int replyThreshold = 7;
    private int replyCount = 0;

    private void updateMicStatus() {
        recording = true;
        if (mMediaRecorder != null && recording) {
            // int vuSize = 10 * mMediaRecorder.getMaxAmplitude() / 32768;
            int ratio = mMediaRecorder.getMaxAmplitude() / BASE;
            db = 0;// 分贝
            if (ratio > 1)
                db = (int) (20 * Math.log10(ratio));
            System.out.println("分贝值：" + db + "     " + Math.log10(ratio));
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    stateTv.setText("分贝:" + db);
                }
            });
            if (tts.isSpeaking()) {
                return;
            }
            if (db < 5) {
                silentCount++;
            } else {
                silentCount = 0;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (silentCount > replyThreshold) {
                        String content = "";
                        if (replyCount < 4) {
                            content = Configure.REPLAY_LIST[replyCount];
                        } else {
                            Random random = new Random();
                            int ran = random.nextInt(Configure.RANDOM_REPLAY_LIST.length);
                            content = Configure.RANDOM_REPLAY_LIST[ran];
                        }
                        replyCount++;
                        conversationContent += content + "\n";
                        conversationTv.setText(conversationContent);
                        text2Speech(content);
                    }
                    mHandler.postDelayed(mUpdateMicStatusTimer, SPACE);
                }
            });
        }
    }

    protected void text2Speech(String text) {
        text2Speech(text, true);
    }

    protected void text2Speech(String text, boolean breakSpeaking) {
        if (tts == null) {
            return;
        }
        if (tts.isSpeaking()) {
            if (breakSpeaking) {
                tts.stop();
            } else {
                return;
            }
        }
        tts.setPitch(0.0f);// 设置音调，值越大声音越尖（女生），值越小则变成男声,1.0是常规
        HashMap<String, String> myHashAlarm = new HashMap();
        myHashAlarm.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
                String.valueOf(AudioManager.STREAM_ALARM));
        tts.speak(text,
                TextToSpeech.QUEUE_FLUSH, myHashAlarm);
    }

    /**
     * 用来初始化TextToSpeech引擎
     * status:SUCCESS或ERROR这2个值
     * setLanguage设置语言，帮助文档里面写了有22种
     * TextToSpeech.LANG_MISSING_DATA：表示语言的数据丢失。
     * TextToSpeech.LANG_NOT_SUPPORTED:不支持
     */
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.US);
            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
//                baseToast.showToast("数据丢失或不支持");
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        tts.stop(); // 不管是否正在朗读TTS都被打断
        tts.shutdown(); // 关闭，释放资源
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.start_btn:
                startRecord();
                break;
            case R.id.stop_btn:
                stopRecord();
                break;
        }
    }
}
