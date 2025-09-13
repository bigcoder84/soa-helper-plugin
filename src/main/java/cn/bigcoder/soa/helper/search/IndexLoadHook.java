package cn.bigcoder.soa.helper.search;

/**
 * @author: Jindong.Tian
 * @date: 2025-07-27
 * @description:
 **/
public interface IndexLoadHook {

    /**
     * 项目索引加载开始
     */
    void beforeProjectIndexLoad();

    /**
     * 项目索引加载完成
     */
    void afterProjectIndexLoad();

    /**
     * 加载服务方法索引前置钩子
     */
    void beforeSoaMethodLoad();


    /**
     * 加载服务方法索引后置钩子
     */
    void afterSoaMethodLoad();
}
