#!/usr/bin/env python3
from __future__ import annotations

import json
import re
from collections import Counter, defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
CASES_PATH = ROOT / "evals" / "chat_cases.jsonl"
SQL_PATH = ROOT / "scripts" / "init-mysql.sql"


CATEGORY_PROFILE = {
    "cat_phone": {
        "category_name": "手机",
        "scenes": ["拍照", "续航", "打游戏", "日常刷视频", "出差", "轻薄便携"],
        "features": ["拍照好一点", "续航久一点", "系统流畅", "性价比高", "夜景拍摄强", "手感轻薄"],
        "budgets": [2999, 3999, 4999, 5999, 6999],
        "compare_focus": ["拍照", "续航", "系统", "性价比"],
        "clarify_templates": [
            "我想买手机，但预算还没想好。",
            "手机想换一个，主要看拍照，但我还没定预算。",
        ],
        "discover_templates": [
            "推荐一台{brand}手机，预算{budget}，主要拍照。",
            "我想买手机，平时{scene}用，预算{budget}。",
            "给我看一台适合{scene}的手机，想要{feature}。",
            "想换手机，预算{budget}，更看重{feature}。",
            "有没有{brand}的手机推荐，预算{budget}，续航要好。",
            "手机我主要拿来{scene}，预算大概{budget}。",
            "我想买一台{feature}更强的手机，预算{budget}。",
            "帮我挑一台手机，{brand}优先，预算{budget}。",
        ],
    },
    "cat_headphone": {
        "category_name": "耳机",
        "scenes": ["通勤", "运动", "开会", "降噪", "长途出行", "听歌"],
        "features": ["降噪强一点", "佩戴舒适", "续航久一点", "音质好一点", "支持通透模式", "适合跑步"],
        "budgets": [599, 999, 1299, 1899, 2299],
        "compare_focus": ["通勤", "降噪", "音质", "运动"],
        "clarify_templates": [
            "我想买耳机，但预算还没定。",
            "耳机我更看重通勤降噪，不过价格先不定。",
        ],
        "discover_templates": [
            "推荐一副{brand}耳机，预算{budget}，主要通勤用。",
            "我想买耳机，平时{scene}用，预算{budget}。",
            "给我看一副适合{scene}的耳机，想要{feature}。",
            "想换耳机，预算{budget}，更看重{feature}。",
            "有没有{brand}的耳机推荐，预算{budget}，通透模式要好。",
            "耳机我主要拿来{scene}，预算大概{budget}。",
            "我想买一副{feature}更强的耳机，预算{budget}。",
            "帮我挑一副耳机，{brand}优先，预算{budget}。",
        ],
    },
    "cat_computer": {
        "category_name": "电脑",
        "scenes": ["办公", "写代码", "轻度游戏", "出差", "轻薄携带", "做表格"],
        "features": ["轻薄一点", "性能强一点", "续航久一点", "屏幕素质高", "散热好一点", "接口丰富"],
        "budgets": [5699, 6999, 7999, 8999, 9999],
        "compare_focus": ["办公", "写代码", "游戏", "轻薄"],
        "clarify_templates": [
            "我想买电脑，但预算还没确定。",
            "电脑想用于办公和写代码，但预算还得再想想。",
        ],
        "discover_templates": [
            "推荐一台{brand}电脑，预算{budget}，主要办公。",
            "我想买电脑，平时{scene}用，预算{budget}。",
            "给我看一台适合{scene}的电脑，想要{feature}。",
            "想换电脑，预算{budget}，更看重{feature}。",
            "有没有{brand}的电脑推荐，预算{budget}，续航要好。",
            "电脑我主要拿来{scene}，预算大概{budget}。",
            "我想买一台{feature}更强的电脑，预算{budget}。",
            "帮我挑一台电脑，{brand}优先，预算{budget}。",
        ],
    },
    "cat_tablet": {
        "category_name": "平板",
        "scenes": ["学习", "记笔记", "追剧", "绘画", "网课", "轻办公"],
        "features": ["屏幕清晰", "手写体验好", "续航久一点", "性能强一点", "轻薄便携", "多任务分屏"],
        "budgets": [2199, 2999, 3999, 4999],
        "compare_focus": ["学习", "记笔记", "追剧", "生产力"],
        "clarify_templates": [
            "我想买平板，但预算先不说。",
            "平板主要给学习用，价格范围我还没想好。",
        ],
        "discover_templates": [
            "推荐一台{brand}平板，预算{budget}，主要学习用。",
            "我想买平板，平时{scene}用，预算{budget}。",
            "给我看一台适合{scene}的平板，想要{feature}。",
            "想换平板，预算{budget}，更看重{feature}。",
            "有没有{brand}的平板推荐，预算{budget}，手写体验要好。",
            "平板我主要拿来{scene}，预算大概{budget}。",
            "我想买一台{feature}更强的平板，预算{budget}。",
            "帮我挑一台平板，{brand}优先，预算{budget}。",
        ],
    },
    "cat_watch": {
        "category_name": "手表",
        "scenes": ["跑步", "健身", "通勤", "健康监测", "睡眠记录", "日常佩戴"],
        "features": ["续航久一点", "健康监测全一点", "轻薄一点", "运动模式多一点", "iPhone 兼容好", "定位准一点"],
        "budgets": [899, 1488, 1999, 2999, 3280],
        "compare_focus": ["跑步", "续航", "健康监测", "iOS生态"],
        "clarify_templates": [
            "我想买手表，但预算还没定。",
            "手表主要跑步用，不过预算我再考虑一下。",
        ],
        "discover_templates": [
            "推荐一块{brand}手表，预算{budget}，主要跑步用。",
            "我想买手表，平时{scene}用，预算{budget}。",
            "给我看一块适合{scene}的手表，想要{feature}。",
            "想换手表，预算{budget}，更看重{feature}。",
            "有没有{brand}的手表推荐，预算{budget}，续航要好。",
            "手表我主要拿来{scene}，预算大概{budget}。",
            "我想买一块{feature}更强的手表，预算{budget}。",
            "帮我挑一块手表，{brand}优先，预算{budget}。",
        ],
    },
    "cat_tv": {
        "category_name": "电视",
        "scenes": ["客厅", "观影", "游戏", "追剧", "体育比赛", "大屏娱乐"],
        "features": ["画质好一点", "刷新率高一点", "尺寸大一点", "亮度高一点", "音响好一点", "系统流畅"],
        "budgets": [4999, 6999, 8999, 11999, 12999],
        "compare_focus": ["画质", "游戏", "客厅", "大屏"],
        "clarify_templates": [
            "我想买电视，但预算还没想好。",
            "电视想放客厅，不过尺寸和预算还不确定。",
        ],
        "discover_templates": [
            "推荐一台{brand}电视，预算{budget}，主要放客厅。",
            "我想买电视，平时{scene}用，预算{budget}。",
            "给我看一台适合{scene}的电视，想要{feature}。",
            "想换电视，预算{budget}，更看重{feature}。",
            "有没有{brand}的电视推荐，预算{budget}，画质要好。",
            "电视我主要拿来{scene}，预算大概{budget}。",
            "我想买一台{feature}更强的电视，预算{budget}。",
            "帮我挑一台电视，{brand}优先，预算{budget}。",
        ],
    },
}


SMALL_TALK_MESSAGES = [
    "你好",
    "早上好",
    "你是谁",
    "谢谢你",
    "你能做什么",
    "你会说中文吗",
    "介绍一下你自己",
    "你在吗",
    "你是聊天机器人吗",
    "可以陪我聊天吗",
    "你喜欢什么",
    "你会唱歌吗",
    "晚安",
    "中午好",
    "你今天怎么样",
    "可以叫你什么",
    "帮我打个招呼",
    "我想跟你闲聊一下",
]


NON_SHOPPING_MESSAGES = [
    "明天天气怎么样",
    "帮我写一段年终总结",
    "把这句话翻译成英文",
    "解释一下什么是机器学习",
    "给我一份深圳三日游攻略",
    "帮我把这段话润色一下",
    "写一封请假邮件",
    "计算 128 的平方根",
    "讲一个笑话",
    "帮我生成一个待办清单",
    "总结一下这段文章",
    "帮我起一个项目名",
    "介绍一下春节",
    "把“你好世界”翻成日语",
    "预测一下今天股市",
    "生成一个 markdown 表格",
    "帮我改一段简历",
    "写一段产品宣传文案",
]


COMPARE_PAIRS = {
    "cat_phone": [
        ("iPhone 15", "小米 14"),
        ("华为 Pura 70", "OPPO Find X7"),
        ("vivo X100", "Samsung Galaxy S24"),
        ("荣耀 200", "Redmi K70"),
    ],
    "cat_headphone": [
        ("AirPods Pro 2", "索尼 WH-1000XM5"),
        ("华为 FreeBuds Pro 3", "小米 Buds 5 Pro"),
        ("Bose QC Ultra", "索尼 WH-1000XM5"),
        ("韶音 OpenRun Pro 2", "Nothing Ear (2)"),
    ],
    "cat_computer": [
        ("MacBook Air M3", "联想小新 Pro 14"),
        ("ThinkPad X1 Carbon", "戴尔灵越 14 Plus"),
        ("华硕天选 4", "机械革命 无界 14X"),
        ("微软 Surface Laptop 6", "惠普战 66 七代"),
    ],
    "cat_tablet": [
        ("iPad Air", "小米平板 6S Pro"),
        ("华为 MatePad Pro", "三星 Galaxy Tab S9"),
        ("联想小新 Pad Pro", "荣耀平板 V8 Pro"),
        ("iPad Air", "华为 MatePad Pro"),
    ],
    "cat_watch": [
        ("Apple Watch S9", "华为 WATCH GT 4"),
        ("Garmin Forerunner 265", "Samsung Galaxy Watch 6"),
        ("小米手表 S3", "华为 WATCH FIT 3"),
        ("Apple Watch S9", "Garmin Forerunner 265"),
    ],
    "cat_tv": [
        ("索尼 XR-65A80L", "小米电视 S Pro 65"),
        ("TCL 75T7K", "海信 E8N Pro 85"),
        ("Redmi 电视 MAX 86", "华为 Vision 智慧屏 5"),
        ("索尼 XR-65A80L", "TCL 75T7K"),
    ],
}


COMPARE_FOCUS = {
    "cat_phone": ["拍照", "续航", "系统", "性价比"],
    "cat_headphone": ["通勤", "降噪", "音质", "运动"],
    "cat_computer": ["办公", "写代码", "游戏", "轻薄"],
    "cat_tablet": ["学习", "记笔记", "追剧", "生产力"],
    "cat_watch": ["跑步", "续航", "健康监测", "iOS 生态"],
    "cat_tv": ["画质", "游戏", "客厅", "大屏"],
}


def load_cases(path: Path) -> list[dict]:
    if not path.exists():
        return []
    cases: list[dict] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        raw = line.strip()
        if raw:
            cases.append(json.loads(raw))
    return cases


def load_catalog(sql_path: Path) -> dict[str, list[dict]]:
    text = sql_path.read_text(encoding="utf-8", errors="ignore")
    row_pattern = re.compile(
        r"\('(?P<sku>SKU\d+)',\s*'(?P<category>[^']*)',\s*'(?P<category_id>[^']*)',\s*'(?P<category_name>[^']*)',\s*'(?P<name>[^']*)',\s*'(?P<brand>[^']*)',\s*(?P<price>\d+),\s*'(?P<description>[^']*)'\)"
    )
    catalog: dict[str, list[dict]] = defaultdict(list)
    for match in row_pattern.finditer(text):
        g = match.groupdict()
        catalog[g["category_id"]].append(
            {
                "skuId": g["sku"],
                "name": g["name"],
                "brand": g["brand"],
                "price": int(g["price"]),
                "categoryName": g["category_name"],
            }
        )
    return catalog


def budget_text(value: int) -> str:
    return f"{value}元"


def make_case(case_id: str, message: str, intent: str, outcome: str, category_id: str | None, expects_tool_use: bool, notes: str) -> dict:
    return {
        "id": case_id,
        "message": message,
        "expected_intent_type": intent,
        "expected_outcome": outcome,
        "expected_category_id": category_id,
        "expects_tool_use": expects_tool_use,
        "notes": notes,
    }


def build_discover_cases(start_index: int, catalog: dict[str, list[dict]]) -> list[dict]:
    cases: list[dict] = []
    current = start_index
    for category_id, profile in CATEGORY_PROFILE.items():
        products = catalog[category_id]
        templates = profile["discover_templates"]
        for i in range(16):
            product = products[i % len(products)]
            brand = product["brand"]
            scene = profile["scenes"][i % len(profile["scenes"])]
            feature = profile["features"][i % len(profile["features"])]
            budget = budget_text(profile["budgets"][i % len(profile["budgets"])])
            message = templates[i % len(templates)].format(
                brand=brand,
                scene=scene,
                feature=feature,
                budget=budget,
            )
            case_id = f"case_{current:03d}_{category_id.replace('cat_', '')}_discover_{i + 1:02d}"
            notes = f"{profile['category_name']}推荐"
            cases.append(make_case(case_id, message, "shopping", "READY_FOR_AGENT", category_id, True, notes))
            current += 1

    # Two extra discovery cases to land exactly on 98 new shopping samples.
    extra_specs = [
        ("cat_phone", "给我推荐一台拍照和续航都不错的手机，预算6000左右。", "手机-综合推荐"),
        ("cat_computer", "推荐一台适合轻薄办公的电脑，预算9000左右。", "电脑-综合推荐"),
    ]
    for category_id, message, notes in extra_specs:
        case_id = f"case_{current:03d}_{category_id.replace('cat_', '')}_discover_extra"
        cases.append(make_case(case_id, message, "shopping", "READY_FOR_AGENT", category_id, True, notes))
        current += 1
    return cases


def build_compare_cases(start_index: int) -> list[dict]:
    cases: list[dict] = []
    current = start_index
    for category_id, pairs in COMPARE_PAIRS.items():
        focus_list = COMPARE_FOCUS[category_id]
        for i, (left, right) in enumerate(pairs):
            focus = focus_list[i % len(focus_list)]
            budgets = {
                "cat_phone": "6000左右",
                "cat_headphone": "2000以内",
                "cat_computer": "9000左右",
                "cat_tablet": "4000左右",
                "cat_watch": "2500左右",
                "cat_tv": "10000左右",
            }
            budget = budgets[category_id]
            templates = [
                "帮我对比 {left} 和 {right}，我更看重{focus}。",
                "请比较一下 {left} 和 {right}，预算{budget}，适合{focus}吗。",
                "我在 {left} 和 {right} 之间纠结，主要用来{focus}。",
                "对比 {left} 与 {right}，哪个更适合{focus}。",
            ]
            message = templates[i % len(templates)].format(left=left, right=right, focus=focus, budget=budget)
            case_id = f"case_{current:03d}_{category_id.replace('cat_', '')}_compare_{i + 1:02d}"
            notes = f"{CATEGORY_PROFILE[category_id]['category_name']}对比"
            cases.append(make_case(case_id, message, "shopping", "READY_FOR_AGENT", category_id, True, notes))
            current += 1
    return cases


def build_clarify_cases(start_index: int) -> list[dict]:
    cases: list[dict] = []
    current = start_index
    for category_id, profile in CATEGORY_PROFILE.items():
        for i, message in enumerate(profile["clarify_templates"]):
            case_id = f"case_{current:03d}_{category_id.replace('cat_', '')}_clarify_{i + 1:02d}"
            notes = f"{profile['category_name']}澄清"
            cases.append(make_case(case_id, message, "shopping", "NEED_CLARIFICATION", category_id, False, notes))
            current += 1
    return cases


def build_smalltalk_cases(start_index: int) -> list[dict]:
    cases: list[dict] = []
    current = start_index
    for i, message in enumerate(SMALL_TALK_MESSAGES):
        case_id = f"case_{current:03d}_smalltalk_{i + 1:02d}"
        cases.append(make_case(case_id, message, "small_talk", "SMALL_TALK", None, False, "闲聊"))
        current += 1
    return cases


def build_nonshopping_cases(start_index: int) -> list[dict]:
    cases: list[dict] = []
    current = start_index
    for i, message in enumerate(NON_SHOPPING_MESSAGES):
        case_id = f"case_{current:03d}_nonshopping_{i + 1:02d}"
        cases.append(make_case(case_id, message, "non_shopping", "NON_SHOPPING", None, False, "非购物"))
        current += 1
    return cases


def main() -> int:
    existing = load_cases(CASES_PATH)
    catalog = load_catalog(SQL_PATH)

    existing_count = len(existing)
    target_total = 200
    if existing_count > target_total:
        raise SystemExit(f"Existing cases already exceed target total: {existing_count} > {target_total}")

    generated: list[dict] = []
    next_index = existing_count + 1
    generated.extend(build_discover_cases(next_index, catalog))
    next_index += 98
    generated.extend(build_compare_cases(next_index))
    next_index += 24
    generated.extend(build_clarify_cases(next_index))
    next_index += 12
    generated.extend(build_smalltalk_cases(next_index))
    next_index += 18
    generated.extend(build_nonshopping_cases(next_index))

    combined = existing + generated
    if len(combined) != target_total:
        raise SystemExit(f"Generated {len(combined)} cases, expected {target_total}")

    ids = [case["id"] for case in combined]
    if len(ids) != len(set(ids)):
        raise SystemExit("Duplicate case ids detected")

    CASES_PATH.write_text(
        "\n".join(json.dumps(case, ensure_ascii=False, separators=(",", ":")) for case in combined) + "\n",
        encoding="utf-8",
    )

    counts = Counter(case["expected_outcome"] for case in combined)
    intents = Counter(case["expected_intent_type"] for case in combined)
    print(json.dumps({
        "total": len(combined),
        "intent_counts": dict(intents),
        "outcome_counts": dict(counts),
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
