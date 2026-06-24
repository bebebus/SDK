module github.com/bebebus/SDK/go

// [M2] 与 README 承诺的最低版本一致（1.21），避免 go.mod 声明过高
// 阻断 1.21~1.25 用户 go get。SDK 仅用标准库，无 1.22+ 语法依赖。
go 1.21
