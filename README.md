<<<<<<< HEAD
# Online Shopping Consultant

电商智能导购多 Agent 系统（Spring Boot + Spring AI Alibaba）。

## 架构（规划）

- **shopping-orchestrator**：总控 Agent，对外 API + Web 聊天页
- **shopping-consult-agent**：咨询导购 Agent
- **shopping-memory-service**：用户长期画像（H2）
- **shopping-catalog / inventory / promotion**：工具服务

会话多轮上下文：Redis。服务发现：Nacos（后续）。

## 仓库

https://github.com/1234563782/OnlineShoppingConsultant

## 开发状态

项目初始化中，实现细节见 Cursor 计划文档。

