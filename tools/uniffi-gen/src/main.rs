use anyhow::Result;
use camino::Utf8Path;
use uniffi_bindgen::{bindings::KotlinBindingGenerator, library_mode, EmptyCrateConfigSupplier};

fn main() -> Result<()> {
    let args: Vec<String> = std::env::args().collect();
    let library = Utf8Path::new(&args[1]);
    let out_dir = Utf8Path::new(&args[2]);
    let comps = library_mode::generate_bindings(
        library,
        None,
        &KotlinBindingGenerator,
        &EmptyCrateConfigSupplier,
        None,
        out_dir,
        false,
    )?;
    for c in &comps {
        println!("generated: {} (crate {})", c.ci.namespace(), c.ci.crate_name());
    }
    Ok(())
}
