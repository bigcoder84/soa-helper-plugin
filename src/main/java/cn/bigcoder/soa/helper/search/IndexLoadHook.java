package cn.bigcoder.soa.helper.search;

/**
 * @author: Jindong.Tian
 * @date: 2025-07-27
 * @description:
 **/
public interface IndexLoadHook {

    /**
     * 索引加载开始
     */
    void startLoad();

    /**
     * 索引加载成功
     */
    void loadSuccess();

}
