/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2022.

*/

package com.exteragram.messenger.preferences;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.components.StickerShapeCell;
import com.exteragram.messenger.components.StickerSizePreviewCell;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;

public class ChatsPreferencesActivity extends BasePreferencesActivity implements NotificationCenter.NotificationCenterDelegate {

    private ActionBarMenuItem resetItem;
    private StickerSizeCell stickerSizeCell;

    private int stickerSizeHeaderRow;
    private int stickerSizeRow;

    private int stickerShapeHeaderRow;
    private int stickerShapeRow;
    private int stickerShapeDividerRow;
    
    private int stickersHeaderRow;
    private int hideStickerTimeRow;
    private int unlimitedRecentStickersRow;
    private int stickersDividerRow;

    private int chatHeaderRow;
    private int hideSendAsChannelRow;
    private int hideKeyboardOnScrollRow;
    private int disableReactionsRow;
    private int disableGreetingStickerRow;
    private int disableJumpToNextChannelRow;
    private int dateOfForwardedMsgRow;
    private int showMessageIDRow;
    private int showActionTimestampsRow;
    private int zalgoFilterRow;
    private int zalgoFilterInfoRow;

    private int mediaHeaderRow;
    private int rearVideoMessagesRow;
    private int disableCameraRow;
    private int disableProximityEventsRow;
    private int pauseOnMinimizeRow;
    private int disablePlaybackRow;
    private int mediaDividerRow;

    private class StickerSizeCell extends FrameLayout {

        private final StickerSizePreviewCell messagesCell;
        private final SeekBarView sizeBar;
        private final int startStickerSize = 4;
        private final int endStickerSize = 20;

        private final TextPaint textPaint;
        private int lastWidth;

        public StickerSizeCell(Context context) {
            super(context);

            setWillNotDraw(false);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(16));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    sizeBar.getSeekBarAccessibilityDelegate().postAccessibilityEventRunnable(StickerSizeCell.this);
                    ExteraConfig.editor.putFloat("stickerSize", ExteraConfig.stickerSize = startStickerSize + (endStickerSize - startStickerSize) * progress).apply();
                    StickerSizeCell.this.invalidate();
                    if (resetItem.getVisibility() != VISIBLE) {
                        AndroidUtilities.updateViewVisibilityAnimated(resetItem, true, 0.5f, true);
                    }
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {

                }
            });
            sizeBar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 9, 5, 43, 11));

            messagesCell = new StickerSizePreviewCell(context, parentLayout);
            messagesCell.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            addView(messagesCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 53, 0, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            canvas.drawText(String.valueOf(Math.round(ExteraConfig.stickerSize)), getMeasuredWidth() - AndroidUtilities.dp(39), AndroidUtilities.dp(28), textPaint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (lastWidth != width) {
                sizeBar.setProgress((ExteraConfig.stickerSize - startStickerSize) / (float) (endStickerSize - startStickerSize));
                lastWidth = width;
            }
        }

        @Override
        public void invalidate() {
            super.invalidate();
            lastWidth = -1;
            messagesCell.invalidate();
            sizeBar.invalidate();
        }

        @Override
        public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(event);
            sizeBar.getSeekBarAccessibilityDelegate().onInitializeAccessibilityEvent(this, event);
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            sizeBar.getSeekBarAccessibilityDelegate().onInitializeAccessibilityNodeInfoInternal(this, info);
        }

        @Override
        public boolean performAccessibilityAction(int action, Bundle arguments) {
            return super.performAccessibilityAction(action, arguments) || sizeBar.getSeekBarAccessibilityDelegate().performAccessibilityActionInternal(this, action, arguments);
        }
    }

    @Override
    public View createView(Context context) {
        View fragmentView = super.createView(context);

        ActionBarMenu menu = actionBar.createMenu();
        resetItem = menu.addItem(0, R.drawable.msg_reset);
        resetItem.setContentDescription(LocaleController.getString("Reset", R.string.Reset));
        resetItem.setVisibility(ExteraConfig.stickerSize != 14.0f ? View.VISIBLE : View.GONE);
        resetItem.setTag(null);
        resetItem.setOnClickListener(v -> {
            AndroidUtilities.updateViewVisibilityAnimated(resetItem, false, 0.5f, true);
            ValueAnimator animator = ValueAnimator.ofFloat(ExteraConfig.stickerSize, 14.0f);
            animator.setDuration(200);
            animator.addUpdateListener(valueAnimator -> {
                ExteraConfig.editor.putFloat("stickerSize", ExteraConfig.stickerSize = (Float) valueAnimator.getAnimatedValue()).apply();
                stickerSizeCell.invalidate();
            });
            animator.start();
        });

        return fragmentView;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        return true;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            if (getListView() != null) {
                getListView().invalidateViews();
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    protected void updateRowsId() {
        super.updateRowsId();

        stickerSizeHeaderRow = newRow();
        stickerSizeRow = newRow();

        stickerShapeHeaderRow = newRow();
        stickerShapeRow = newRow();
        stickerShapeDividerRow = newRow();

        stickersHeaderRow = newRow();
        hideStickerTimeRow = newRow();
        unlimitedRecentStickersRow = newRow();
        stickersDividerRow = newRow();

        chatHeaderRow = newRow();
        hideSendAsChannelRow = newRow();
        hideKeyboardOnScrollRow = newRow();
        disableReactionsRow = newRow();
        disableGreetingStickerRow = newRow();
        disableJumpToNextChannelRow = newRow();
        dateOfForwardedMsgRow = newRow();
        showMessageIDRow = newRow();
        showActionTimestampsRow = newRow();
        zalgoFilterRow = newRow();
        zalgoFilterInfoRow = newRow();

        mediaHeaderRow = newRow();
        rearVideoMessagesRow = newRow();
        disableCameraRow = newRow();
        disableProximityEventsRow = newRow();
        pauseOnMinimizeRow = newRow();
        disablePlaybackRow = newRow();
        mediaDividerRow = newRow();
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == hideStickerTimeRow) {
            ExteraConfig.editor.putBoolean("hideStickerTime", ExteraConfig.hideStickerTime ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.hideStickerTime);
            stickerSizeCell.invalidate();
        } else if (position == unlimitedRecentStickersRow) {
            ExteraConfig.editor.putBoolean("unlimitedRecentStickers", ExteraConfig.unlimitedRecentStickers ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.unlimitedRecentStickers);
        } else if (position == hideSendAsChannelRow) {
            ExteraConfig.editor.putBoolean("hideSendAsChannel", ExteraConfig.hideSendAsChannel ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.hideSendAsChannel);
        } else if (position == hideKeyboardOnScrollRow) {
            ExteraConfig.editor.putBoolean("hideKeyboardOnScroll", ExteraConfig.hideKeyboardOnScroll ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.hideKeyboardOnScroll);
        } else if (position == disableReactionsRow) {
            ExteraConfig.editor.putBoolean("disableReactions", ExteraConfig.disableReactions ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.disableReactions);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == disableGreetingStickerRow) {
            ExteraConfig.editor.putBoolean("disableGreetingSticker", ExteraConfig.disableGreetingSticker ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.disableGreetingSticker);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == disableJumpToNextChannelRow) {
            ExteraConfig.editor.putBoolean("disableJumpToNextChannel", ExteraConfig.disableJumpToNextChannel ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.disableJumpToNextChannel);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == dateOfForwardedMsgRow) {
            ExteraConfig.editor.putBoolean("dateOfForwardedMsg", ExteraConfig.dateOfForwardedMsg ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.dateOfForwardedMsg);
        } else if (position == showMessageIDRow) {
            ExteraConfig.editor.putBoolean("showMessageID", ExteraConfig.showMessageID ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.showMessageID);
        } else if (position == showActionTimestampsRow) {
            ExteraConfig.editor.putBoolean("showActionTimestamps", ExteraConfig.showActionTimestamps ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.showActionTimestamps);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == zalgoFilterRow) {
            ExteraConfig.editor.putBoolean("zalgoFilter", ExteraConfig.zalgoFilter ^= true).apply();
            getMessagesController().clearQueryTime();
            getMessagesStorage().clearLocalDatabase();
            ((TextCheckCell) view).setChecked(ExteraConfig.zalgoFilter);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == rearVideoMessagesRow) {
            ExteraConfig.editor.putBoolean("rearVideoMessages", ExteraConfig.rearVideoMessages ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.rearVideoMessages);
        } else if (position == disableCameraRow) {
            ExteraConfig.editor.putBoolean("disableCamera", ExteraConfig.disableCamera ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.disableCamera);
        } else if (position == disableProximityEventsRow) {
            ExteraConfig.editor.putBoolean("disableProximityEvents", ExteraConfig.disableProximityEvents ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.disableProximityEvents);
        } else if (position == pauseOnMinimizeRow) {
            ExteraConfig.editor.putBoolean("pauseOnMinimize", ExteraConfig.pauseOnMinimize ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.pauseOnMinimize);
        } else if (position == disablePlaybackRow) {
            ExteraConfig.editor.putBoolean("disablePlayback", ExteraConfig.disablePlayback ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.disablePlayback);
            showBulletin();
        }
    }

    @Override
    protected String getTitle() {
        return LocaleController.getString("SearchAllChatsShort", R.string.SearchAllChatsShort);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            switch (type) {
                case 10:
                    StickerShapeCell stickerShapeCell = new StickerShapeCell(mContext) {
                        @Override
                        protected void updateStickerPreview() {
                            parentLayout.rebuildAllFragmentViews(false, false);
                            stickerSizeCell.invalidate();
                        }
                    };
                    stickerShapeCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    stickerShapeCell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(stickerShapeCell);
                case 11:
                    stickerSizeCell = new StickerSizeCell(mContext);
                    stickerSizeCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    stickerSizeCell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(stickerSizeCell);
                default:
                    return super.onCreateViewHolder(parent, type);
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 1:
                    if (position == mediaDividerRow) {
                        holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                case 3:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == stickerSizeHeaderRow) {
                        headerCell.setText(LocaleController.getString("StickerSize", R.string.StickerSize));
                    } else if (position == stickersHeaderRow) {
                        headerCell.setText(LocaleController.getString("AccDescrStickers", R.string.AccDescrStickers));
                    } else if (position == chatHeaderRow) {
                        headerCell.setText(LocaleController.getString("SearchAllChatsShort", R.string.SearchAllChatsShort));
                    } else if (position == mediaHeaderRow) {
                        headerCell.setText(LocaleController.getString("Media", R.string.Media));
                    } else if (position == stickerShapeHeaderRow) {
                        headerCell.setText(LocaleController.getString("StickerShape", R.string.StickerShape));
                    }
                    break;
                case 5:
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    textCheckCell.setEnabled(true, null);
                    if (position == hideStickerTimeRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("StickerTime", R.string.StickerTime), ExteraConfig.hideStickerTime, true);
                    } else if (position == unlimitedRecentStickersRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("UnlimitedRecentStickers", R.string.UnlimitedRecentStickers), ExteraConfig.unlimitedRecentStickers, false);
                    } else if (position == hideSendAsChannelRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("HideSendAsChannel", R.string.HideSendAsChannel), ExteraConfig.hideSendAsChannel, true);
                    } else if (position == hideKeyboardOnScrollRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("HideKeyboardOnScroll", R.string.HideKeyboardOnScroll), ExteraConfig.hideKeyboardOnScroll, true);
                    } else if (position == disableReactionsRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("DisableReactions", R.string.DisableReactions), ExteraConfig.disableReactions, true);
                    } else if (position == disableGreetingStickerRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("DisableGreetingSticker", R.string.DisableGreetingSticker), ExteraConfig.disableGreetingSticker, true);
                    } else if (position == disableJumpToNextChannelRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("DisableJumpToNextChannel", R.string.DisableJumpToNextChannel), ExteraConfig.disableJumpToNextChannel, true);
                    } else if (position == dateOfForwardedMsgRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("DateOfForwardedMsg", R.string.DateOfForwardedMsg), ExteraConfig.dateOfForwardedMsg, true);
                    } else if (position == showMessageIDRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("ShowMessageID", R.string.ShowMessageID), ExteraConfig.showMessageID, true);
                    } else if (position == showActionTimestampsRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("ShowActionTimestamps", R.string.ShowActionTimestamps), ExteraConfig.showActionTimestamps, true);
                    } else if (position == zalgoFilterRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("ZalgoFilter", R.string.ZalgoFilter), ExteraConfig.zalgoFilter, false);
                    } else if (position == rearVideoMessagesRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("RearVideoMessages", R.string.RearVideoMessages), ExteraConfig.rearVideoMessages, true);
                    } else if (position == disableCameraRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("DisableCamera", R.string.DisableCamera), ExteraConfig.disableCamera, true);
                    } else if (position == disableProximityEventsRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("DisableProximityEvents", R.string.DisableProximityEvents), ExteraConfig.disableProximityEvents, true);
                    } else if (position == pauseOnMinimizeRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.getString("PauseOnMinimize", R.string.PauseOnMinimize), LocaleController.getString("POMDescription", R.string.POMDescription), ExteraConfig.pauseOnMinimize, true, true);
                    } else if (position == disablePlaybackRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.getString("DisablePlayback", R.string.DisablePlayback), LocaleController.getString("DPDescription", R.string.DPDescription), ExteraConfig.disablePlayback, true, false);
                    }
                    break;
                case 8:
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == zalgoFilterInfoRow) {
                        cell.setText(LocaleController.getString("ZalgoFilterInfo", R.string.ZalgoFilterInfo));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == stickersDividerRow || position == mediaDividerRow || position == stickerShapeDividerRow) {
                return 1;
            } else if (position == stickerSizeHeaderRow || position == stickersHeaderRow || position == chatHeaderRow || position == mediaHeaderRow || position == stickerShapeHeaderRow) {
                return 3;
            } else if (position == zalgoFilterInfoRow) {
                return 8;
            } else if (position == stickerShapeRow) {
                return 10;
            } else if (position == stickerSizeRow) {
                return 11;
            }
            return 5;
        }
    }
}