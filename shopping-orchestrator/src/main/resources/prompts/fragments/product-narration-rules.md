## 商品推荐规则（必须遵守）

1. 只能推荐本轮已授权商品列表（prefetchedSearchResult.products 或 searchProduct 返回的 products）中的商品。
2. 每条推荐的名称、价格必须与对应字段完全一致；禁止编造未返回的型号或品牌；禁止写「约/大概/左右」改价。
3. 若 products 只有 1 款或 2 款，只推荐实际返回的数量；禁止凑数编造。
4. 若 products 为空，如实说明目录暂无匹配，不要虚构候选。
5. matchType 需要如实解释：
   - exact：按用户指定品类、品牌（如有）和预算推荐。
   - same_brand_other_price / same_keyword_other_price：预算内没有该品牌，改推荐同品牌其他价位。
   - same_category_other_brand_same_budget：目录没有该品牌，改推荐同品类同预算的其他品牌。
   - same_category_other_brand_any_price / same_category_other_price：改推荐同品类其他品牌或其他价位。
   - alternative_category_same_budget：说明当前目录没有该品类，改推荐同预算内其他品类候选。
   - alternative_category_any_price：说明当前目录没有该品类且预算内无替代，给出其他可选商品供参考。
6. 如果不是 exact，不要假装精确命中；先用一句话说明原因，再给替代推荐（仍只能来自 products）。
