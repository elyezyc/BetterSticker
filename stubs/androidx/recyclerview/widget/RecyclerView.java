package androidx.recyclerview.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

/**
 * 仅用于编译的 RecyclerView 桩（不会打包进 APK）。
 * 运行时由 QQ 自带的 androidx.recyclerview 提供真实类。
 *
 * 只声明 SettingsAdapterWrapper 用到的方法/嵌套类型，签名与 RecyclerView 1.3.x 对齐。
 */
public class RecyclerView extends ViewGroup {

    public RecyclerView(Context context) {
        super(context);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
    }

    public Adapter<?> getAdapter() {
        return null;
    }

    public void setAdapter(Adapter<?> adapter) {
    }

    public static abstract class ViewHolder {
        public final View itemView;

        public ViewHolder(View itemView) {
            this.itemView = itemView;
        }
    }

    public static abstract class Adapter<VH extends ViewHolder> {

        public abstract int getItemCount();

        public int getItemViewType(int position) {
            return 0;
        }

        public abstract VH onCreateViewHolder(ViewGroup parent, int viewType);

        public abstract void onBindViewHolder(VH holder, int position);

        public void onBindViewHolder(VH holder, int position, List<Object> payloads) {
            onBindViewHolder(holder, position);
        }

        public long getItemId(int position) {
            return -1;
        }

        public void setHasStableIds(boolean hasStableIds) {
        }

        public boolean hasStableIds() {
            return false;
        }

        public void onViewRecycled(VH holder) {
        }

        public boolean onFailedToRecycleView(VH holder) {
            return false;
        }

        public void onViewAttachedToWindow(VH holder) {
        }

        public void onViewDetachedFromWindow(VH holder) {
        }

        public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        }

        public void onDetachedFromRecyclerView(RecyclerView recyclerView) {
        }

        public void registerAdapterDataObserver(AdapterDataObserver observer) {
        }

        public void unregisterAdapterDataObserver(AdapterDataObserver observer) {
        }

        public final void notifyDataSetChanged() {
        }

        public static abstract class AdapterDataObserver {
            public void onChanged() {
            }

            public void onItemRangeChanged(int positionStart, int itemCount) {
            }

            public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
            }

            public void onItemRangeInserted(int positionStart, int itemCount) {
            }

            public void onItemRangeRemoved(int positionStart, int itemCount) {
            }

            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            }
        }
    }
}
