package com.example.mywifiscanner;

import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

// 独立管理网格显示与比例的模块
public class GridControlModule {
    private CheckBox cbShowGrid;
    private SeekBar sbGridScale;
    private TextView tvScaleValue;
    private MapData.GridConfig gridConfig; // 网格配置（可从文件加载）
    private OnGridChangeListener listener; // 网格变化回调（通知地图绘制）

    // 构造函数注入控件和初始配置
    public GridControlModule(CheckBox cbShowGrid, SeekBar sbGridScale,
                             TextView tvScaleValue, MapData.GridConfig savedConfig) {
        this.cbShowGrid = cbShowGrid;
        this.sbGridScale = sbGridScale;
        this.tvScaleValue = tvScaleValue;
        this.gridConfig = savedConfig != null ? savedConfig : new MapData.GridConfig();
        initView();
        initListener();
    }

    // 初始化控件状态（恢复上次保存的配置）
    private void initView() {
        cbShowGrid.setChecked(gridConfig.isShow());
        sbGridScale.setProgress(gridConfig.getScale());
        tvScaleValue.setText(gridConfig.getScale() + "px");
    }

    private void initListener() {
        // 网格显示/隐藏切换
        cbShowGrid.setOnCheckedChangeListener((buttonView, isChecked) -> {
            gridConfig.setShow(isChecked);
            if (listener != null) {
                listener.onGridChange(gridConfig); // 通知地图刷新
            }
        });

        // 网格比例调整
        sbGridScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                gridConfig.setScale(progress);
                tvScaleValue.setText(progress + "px");
                if (listener != null && cbShowGrid.isChecked()) {
                    listener.onGridChange(gridConfig); // 实时刷新网格
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    // 对外提供当前网格配置（用于保存到JSON）
    public MapData.GridConfig getGridConfig() {
        return gridConfig;
    }

    // 网格变化监听器（供地图模块实现绘制）
    public interface OnGridChangeListener {
        void onGridChange(MapData.GridConfig config);
    }

    public void setOnGridChangeListener(OnGridChangeListener listener) {
        this.listener = listener;
    }
}