package com.example.mywifiscanner;

import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;

// 独立的输入框切换模块，仅处理“特殊点/普通点”的UI逻辑
public class InputSwitchModule {
    private RadioGroup rgPointType;
    private EditText etLabel; // 特殊点标签输入框
    private EditText etPath; // 普通点路径输入框

    // 构造函数注入控件，避免直接依赖Activity
    public InputSwitchModule(RadioGroup rgPointType, EditText etLabel, EditText etPath) {
        this.rgPointType = rgPointType;
        this.etLabel = etLabel;
        this.etPath = etPath;
        initListener(); // 初始化监听
    }

    private void initListener() {
        rgPointType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbSpecialPoint) {
                // 特殊点：显示label，隐藏path
                etLabel.setVisibility(View.VISIBLE);
                etPath.setVisibility(View.GONE);
                etLabel.setHint("请输入特殊点标签（如：电梯口）");
            } else {
                // 普通点：显示path，隐藏label
                etLabel.setVisibility(View.GONE);
                etPath.setVisibility(View.VISIBLE);
                etPath.setHint("请输入路径描述（如：距电梯口3米）");
            }
        });

        // 默认选中特殊点
        rgPointType.check(R.id.rbSpecialPoint);
    }

    // 对外提供获取输入值的接口（解耦UI与数据收集）
    public MapData.PointData getPointData() {
        MapData.PointData data = new MapData.PointData();
        data.setSpecial(rgPointType.getCheckedRadioButtonId() == R.id.rbSpecialPoint);
        if (data.isSpecial()) {
            data.setLabel(etLabel.getText().toString().trim());
        } else {
            data.setPath(etPath.getText().toString().trim());
        }
        return data;
    }
}