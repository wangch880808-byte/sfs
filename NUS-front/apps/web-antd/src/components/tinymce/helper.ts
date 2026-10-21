import { getRequestErrorReason } from '#/utils/request-error';

const validEvents = new Set([
  'onActivate',
  'onAddUndo',
  'onBeforeAddUndo',
  'onBeforeExecCommand',
  'onBeforeGetContent',
  'onBeforePaste',
  'onBeforeRenderUI',
  'onBeforeSetContent',
  'onBlur',
  'onChange',
  'onClearUndos',
  'onClick',
  'onContextMenu',
  'onCopy',
  'onCut',
  'onDblclick',
  'onDeactivate',
  'onDirty',
  'onDrag',
  'onDragDrop',
  'onDragEnd',
  'onDragGesture',
  'onDragOver',
  'onDrop',
  'onExecCommand',
  'onFocus',
  'onFocusIn',
  'onFocusOut',
  'onGetContent',
  'onHide',
  'onInit',
  'onKeyDown',
  'onKeyPress',
  'onKeyUp',
  'onLoadContent',
  'onMouseDown',
  'onMouseEnter',
  'onMouseLeave',
  'onMouseMove',
  'onMouseOut',
  'onMouseOver',
  'onMouseUp',
  'onNodeChange',
  'onObjectResized',
  'onObjectResizeStart',
  'onObjectSelected',
  'onPaste',
  'onPostProcess',
  'onPostRender',
  'onPreProcess',
  'onProgressState',
  'onRedo',
  'onRemove',
  'onReset',
  'onSaveContent',
  'onSelectionChange',
  'onSetAttrib',
  'onSetContent',
  'onShow',
  'onSubmit',
  'onUndo',
  'onVisualAid',
]);

const isValidKey = (key: string) => validEvents.has(key);

export const bindHandlers = (
  initEvent: Event,
  listeners: any,
  editor: any,
): void => {
  Object.keys(listeners)
    .filter((element) => isValidKey(element))
    .forEach((key: string) => {
      const handler = listeners[key];
      if (typeof handler === 'function') {
        if (key === 'onInit') {
          handler(initEvent, editor);
        } else {
          editor.on(key.slice(2), (e: any) => handler(e, editor));
        }
      }
    });
};

/**
 * 粘贴、拖入或在插入图片对话框里上传的图片没有传上去时，交给编辑器的失败信息。
 * 上传请求不弹全局提示（silentError），只由编辑器提示这一条（内容区里的提示前面会带上「图片上传失败」）；
 * remove: true 让编辑器把没传上去的图片从内容里拿掉，不留下本地临时图片。
 */
export function toImageUploadFailure(error: unknown): {
  message: string;
  remove: boolean;
} {
  const reason = getRequestErrorReason(error) || '原因未知';
  return {
    message: `${reason}。这张图片没有插入，可以重新粘贴或上传。`,
    remove: true,
  };
}
