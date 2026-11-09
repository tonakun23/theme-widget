//! Sinh `schema/theme.schema.json` từ struct Rust (schemars).
//! Chạy: `cargo run -p theme-core --bin gen_schema`
use schemars::schema_for;
use std::io::Write;

fn main() {
    let schema = schema_for!(theme_core::Theme);
    let json = serde_json::to_string_pretty(&schema).expect("serialize schema");
    let out =
        std::path::PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../schema/theme.schema.json");
    let mut f = std::fs::File::create(&out).expect("create schema file");
    f.write_all(json.as_bytes()).expect("write schema");
    f.write_all(b"\n").expect("write newline");
    println!("wrote {}", out.display());
}
