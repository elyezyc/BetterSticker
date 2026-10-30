package com.bettersticker.qq;

import java.util.List;

/** QQ 适配器接口；实现失败时必须返回空列表/抛异常，不能伪造数据。 */
public interface QqAdapter {

    List<QqMedia> extract(Object aioMsgItem) throws Exception;
}
