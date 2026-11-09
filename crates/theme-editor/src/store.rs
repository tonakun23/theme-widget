//! Kho theme: nhân bản, đổi tên, xóa (BR-04, BR-05, BR-23, UC-05).
use theme_core::{Theme, ThemeError, ThemeKind};

/// Tối đa 200 theme người dùng trên một máy (BR-23).
pub const MAX_USER_THEMES: usize = 200;

/// Kiểm tra tên theme: 1–40 ký tự (BR-05, E-02).
pub fn validate_theme_name(name: &str) -> Result<(), ThemeError> {
    let trimmed = name.trim();
    if trimmed.is_empty() || trimmed.chars().count() > 40 {
        return Err(ThemeError::InvalidName);
    }
    Ok(())
}

#[derive(Debug, Default)]
pub struct ThemeStore {
    pub themes: Vec<Theme>,
}

impl ThemeStore {
    pub fn new(themes: Vec<Theme>) -> Self {
        ThemeStore { themes }
    }

    pub fn get(&self, id: &str) -> Option<&Theme> {
        self.themes.iter().find(|t| t.id == id)
    }

    pub fn user_themes(&self) -> Vec<&Theme> {
        self.themes
            .iter()
            .filter(|t| t.kind != ThemeKind::BuiltIn)
            .collect()
    }

    fn name_taken(&self, name: &str) -> bool {
        let lower = name.trim().to_lowercase();
        self.user_themes()
            .iter()
            .any(|t| t.name.trim().to_lowercase() == lower)
    }

    /// Thêm theme người dùng mới (kiểm tra tên + giới hạn 200, BR-05/BR-23).
    pub fn add(&mut self, theme: Theme) -> Result<(), ThemeError> {
        validate_theme_name(&theme.name)?;
        if theme.kind != ThemeKind::BuiltIn && self.name_taken(&theme.name) {
            return Err(ThemeError::DuplicateName(theme.name.clone()));
        }
        if theme.kind != ThemeKind::BuiltIn && self.user_themes().len() >= MAX_USER_THEMES {
            return Err(ThemeError::TooManyThemes);
        }
        self.themes.push(theme);
        Ok(())
    }

    /// Nhân bản theme có sẵn để chỉnh sửa (BR-04): tạo theme người dùng mới,
    /// tên "<Tên theme> (bản của tôi)", trùng thì thêm hậu tố "(2)", "(3)"...
    pub fn clone_builtin(&mut self, id: &str) -> Result<Theme, ThemeError> {
        let src = self
            .get(id)
            .ok_or_else(|| ThemeError::ThemeNotFound(id.to_string()))?
            .clone();
        if src.kind != ThemeKind::BuiltIn {
            return Err(ThemeError::InvalidValue(
                "Chỉ nhân bản được theme có sẵn".to_string(),
            ));
        }
        if self.user_themes().len() >= MAX_USER_THEMES {
            return Err(ThemeError::TooManyThemes);
        }
        let base = format!("{} (bản của tôi)", src.name);
        let mut name = base.clone();
        let mut n = 2;
        while self.name_taken(&name) {
            name = format!("{base} ({n})");
            n += 1;
        }
        let mut clone = src;
        // Id duy nhất và ổn định: custom-<id gốc>-<số thứ tự chưa dùng>.
        // (Không dùng mili-giây: 2 lần nhân bản trong cùng 1 ms sẽ trùng id.)
        let mut n = 1;
        let mut new_id = format!("custom-{}-{n}", clone.id);
        while self.get(&new_id).is_some() {
            n += 1;
            new_id = format!("custom-{}-{n}", clone.id);
        }
        clone.id = new_id;
        clone.name = name;
        clone.kind = ThemeKind::Custom;
        clone.version = 1;
        self.themes.push(clone.clone());
        Ok(clone)
    }

    /// Đổi tên theme người dùng (BR-05).
    pub fn rename(&mut self, id: &str, new_name: &str) -> Result<(), ThemeError> {
        validate_theme_name(new_name)?;
        let lower = new_name.trim().to_lowercase();
        if self
            .user_themes()
            .iter()
            .any(|t| t.id != id && t.name.trim().to_lowercase() == lower)
        {
            return Err(ThemeError::DuplicateName(new_name.to_string()));
        }
        let theme = self
            .themes
            .iter_mut()
            .find(|t| t.id == id)
            .ok_or_else(|| ThemeError::ThemeNotFound(id.to_string()))?;
        if theme.kind == ThemeKind::BuiltIn {
            return Err(ThemeError::InvalidValue(
                "Theme có sẵn không đổi tên được".to_string(),
            ));
        }
        theme.name = new_name.trim().to_string();
        Ok(())
    }

    /// Xóa theme người dùng (BR-15). Theme có sẵn không xóa được (BR-03).
    /// Caller (Kotlin) chịu trách nhiệm chuyển widget đang dùng theme này
    /// về theme "Mặc định" trước khi gọi.
    pub fn delete(&mut self, id: &str) -> Result<Theme, ThemeError> {
        let pos = self
            .themes
            .iter()
            .position(|t| t.id == id)
            .ok_or_else(|| ThemeError::ThemeNotFound(id.to_string()))?;
        if self.themes[pos].kind == ThemeKind::BuiltIn {
            return Err(ThemeError::InvalidValue(
                "Theme có sẵn không xóa được".to_string(),
            ));
        }
        Ok(self.themes.remove(pos))
    }
}
