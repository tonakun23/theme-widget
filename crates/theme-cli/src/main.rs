//! theme-cli — Công cụ dòng lệnh cho nhà thiết kế theme (F-24).
//!
//! ```sh
//! theme-cli validate themes/mac-dinh.json
//! theme-cli resolve themes/mac-dinh.json --dark
//! theme-cli list-themes themes/
//! ```
use clap::{Parser, Subcommand};
use std::collections::HashMap;
use std::path::PathBuf;
use theme_core::{
    default_theme, load_themes_from_dir, resolve_theme, theme_from_json, validate_theme, Colors,
    Variant, VariantMode, DEFAULT_THEME_ID,
};

#[derive(Parser)]
#[command(
    name = "theme-cli",
    about = "Kiểm tra, gộp và liệt kê theme cho app Theme Widget"
)]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// Kiểm tra một file theme JSON (màu, tên, schema, tương phản).
    Validate {
        /// Đường dẫn file theme JSON.
        file: PathBuf,
    },
    /// Gộp theme thành theme cuối cùng (BR-24) và in ra JSON + cảnh báo.
    Resolve {
        /// Đường dẫn file theme JSON.
        file: PathBuf,
        /// Gộp biến thể Tối thay vì Sáng.
        #[arg(long)]
        dark: bool,
        /// Thư mục chứa các theme cha / theme Mặc định (mặc định: thư mục của file).
        #[arg(long)]
        themes_dir: Option<PathBuf>,
        /// File JSON chứa ghi đè widget (Variant) — ưu tiên cao nhất (BR-12).
        #[arg(long)]
        overrides: Option<PathBuf>,
    },
    /// Liệt kê mọi theme trong một thư mục.
    ListThemes {
        /// Thư mục chứa theme JSON.
        dir: PathBuf,
    },
}

fn build_registry(
    dir: &std::path::Path,
    extra: Option<ThemeFile>,
) -> HashMap<String, theme_core::Theme> {
    let mut reg: HashMap<String, theme_core::Theme> = HashMap::new();
    reg.insert(DEFAULT_THEME_ID.to_string(), default_theme());
    if let Ok(themes) = load_themes_from_dir(dir) {
        for t in themes {
            reg.insert(t.id.clone(), t);
        }
    }
    if let Some(f) = extra {
        reg.insert(f.theme.id.clone(), f.theme);
    }
    reg
}

struct ThemeFile {
    theme: theme_core::Theme,
}

fn main() {
    let cli = Cli::parse();
    let code = run(cli);
    std::process::exit(code);
}

fn run(cli: Cli) -> i32 {
    match cli.command {
        Commands::Validate { file } => {
            let text = match std::fs::read_to_string(&file) {
                Ok(t) => t,
                Err(e) => {
                    eprintln!("Không đọc được {}: {e}", file.display());
                    return 2;
                }
            };
            let theme = match theme_from_json(&text) {
                Ok(t) => t,
                Err(e) => {
                    eprintln!("Theme không hợp lệ: {e}");
                    return 2;
                }
            };
            match validate_theme(&theme) {
                Ok(warnings) => {
                    println!(
                        "OK: {} (id={}, schema v{})",
                        theme.name, theme.id, theme.schema_version
                    );
                    for w in warnings {
                        println!("CẢNH BÁO [{}]: {}", w.code, w.message);
                    }
                    0
                }
                Err(e) => {
                    eprintln!("Theme không hợp lệ: {e}");
                    2
                }
            }
        }
        Commands::Resolve {
            file,
            dark,
            themes_dir,
            overrides,
        } => {
            let text = match std::fs::read_to_string(&file) {
                Ok(t) => t,
                Err(e) => {
                    eprintln!("Không đọc được {}: {e}", file.display());
                    return 2;
                }
            };
            let theme = match theme_from_json(&text) {
                Ok(t) => t,
                Err(e) => {
                    eprintln!("Theme không hợp lệ: {e}");
                    return 2;
                }
            };
            let dir = themes_dir.unwrap_or_else(|| {
                file.parent()
                    .map(|p| p.to_path_buf())
                    .unwrap_or_else(|| PathBuf::from("."))
            });
            let reg = build_registry(
                &dir,
                Some(ThemeFile {
                    theme: theme.clone(),
                }),
            );
            let ov: Option<Variant> = match overrides {
                Some(p) => match std::fs::read_to_string(&p) {
                    Ok(t) => match serde_json::from_str(&t) {
                        Ok(v) => Some(v),
                        Err(e) => {
                            eprintln!("Ghi đè không hợp lệ: {e}");
                            return 2;
                        }
                    },
                    Err(e) => {
                        eprintln!("Không đọc được ghi đè: {e}");
                        return 2;
                    }
                },
                None => None,
            };
            let mode = if dark {
                VariantMode::Dark
            } else {
                VariantMode::Light
            };
            // Không có màu hệ thống thật trên máy tính: truyền None.
            let system: Option<&Colors> = None;
            match resolve_theme(&reg, &theme.id, mode, system, ov.as_ref()) {
                Ok(resolved) => {
                    println!("{}", serde_json::to_string_pretty(&resolved).unwrap());
                    if let Ok(warnings) = validate_theme(&theme) {
                        for w in warnings {
                            eprintln!("CẢNH BÁO [{}]: {}", w.code, w.message);
                        }
                    }
                    0
                }
                Err(e) => {
                    eprintln!("Không gộp được theme: {e}");
                    2
                }
            }
        }
        Commands::ListThemes { dir } => match load_themes_from_dir(&dir) {
            Ok(themes) => {
                println!("{} theme trong {}", themes.len(), dir.display());
                for t in themes {
                    println!(
                        "- {} [{}] kind={:?} extends={} dark={}",
                        t.id,
                        t.name,
                        t.kind,
                        t.extends.as_deref().unwrap_or("-"),
                        if t.dark.is_some() { "có" } else { "tự sinh" }
                    );
                }
                0
            }
            Err(e) => {
                eprintln!("Không đọc được thư mục: {e}");
                2
            }
        },
    }
}
