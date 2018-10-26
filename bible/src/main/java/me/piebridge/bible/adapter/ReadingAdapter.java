package me.piebridge.bible.adapter;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import me.piebridge.bible.activity.AbstractReadingActivity;
import me.piebridge.bible.fragment.ReadingFragment;
import me.piebridge.bible.utils.LogUtils;

import static me.piebridge.bible.activity.AbstractReadingActivity.POSITION;

/**
 * Created by thom on 16/7/2.
 */
public class ReadingAdapter extends FragmentStatePagerAdapter {

    private int mSize;

    private final SparseArray<Bundle> mBundles;

    private final SparseArray<ReadingFragment> mFragments;

    public ReadingAdapter(FragmentManager fm, int size) {
        super(fm);
        mSize = size;
        mBundles = new SparseArray<>();
        mFragments = new SparseArray<>();
    }

    public void setSize(int size) {
        mSize = size;
    }

    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        ReadingFragment fragment = (ReadingFragment) super.instantiateItem(container, position);
        mFragments.put(position, fragment);
        Bundle arguments = fragment.getArguments();
        if (arguments == null || TextUtils.isEmpty(arguments.getString(AbstractReadingActivity.CURR))) {
            LogUtils.w("empty arguments: " + arguments);
        }
        return fragment;
    }

    @Override
    public ReadingFragment getItem(int position) {
        ReadingFragment fragment = new ReadingFragment();
        fragment.setArguments(getData(position));
        return fragment;
    }

    @Override
    public int getCount() {
        return mSize;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        super.destroyItem(container, position, object);
        mFragments.remove(position);
        mBundles.remove(position);
    }

    @Override
    public int getItemPosition(@NonNull Object object) {
        LogUtils.d("object: " + object);
        if (object instanceof ReadingFragment) {
            ReadingFragment fragment = (ReadingFragment) object;
            Bundle arguments = fragment.getArguments();
            if (arguments != null && arguments.getInt(AbstractReadingActivity.ID) > mSize) {
                return POSITION_NONE;
            }
        }
        return POSITION_UNCHANGED;
    }

    public ReadingFragment getFragment(int position) {
        return mFragments.get(position);
    }

    public synchronized void setData(int position, Bundle bundle) {
        bundle.putInt(POSITION, position);
        mBundles.put(position, bundle);
    }

    public synchronized Bundle getData(int position) {
        Bundle bundle = mBundles.get(position);
        if (bundle == null) {
            bundle = new Bundle();
            mBundles.put(position, bundle);
        }
        return bundle;
    }

}
